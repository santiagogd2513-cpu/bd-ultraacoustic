#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>
#include <atomic>
#include <thread>
#include <vector>
#include <fstream>
#include <iterator>
#include <cstring>
#include <algorithm>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"ColombiriaVK",__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"ColombiriaVK",__VA_ARGS__)

namespace {
struct Engine {
    ANativeWindow* window=nullptr;
    std::thread thread;
    std::atomic<bool> running{false};
    std::atomic<bool> throttle{false};
    std::atomic<float> steering{0.f};
    VkInstance instance=VK_NULL_HANDLE;
    VkSurfaceKHR surface=VK_NULL_HANDLE;
    VkPhysicalDevice gpu=VK_NULL_HANDLE;
    VkDevice device=VK_NULL_HANDLE;
    uint32_t queueFamily=0;
    VkQueue queue=VK_NULL_HANDLE;
    VkSwapchainKHR swapchain=VK_NULL_HANDLE;
    VkFormat format=VK_FORMAT_UNDEFINED;
    VkExtent2D extent{};
    std::vector<VkImage> images;
    std::vector<VkImageView> views;
    VkRenderPass renderPass=VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout=VK_NULL_HANDLE;
    VkPipeline pipeline=VK_NULL_HANDLE;
    std::vector<VkFramebuffer> framebuffers;
    VkCommandPool commandPool=VK_NULL_HANDLE;
    std::vector<VkCommandBuffer> commands;
    VkSemaphore acquired=VK_NULL_HANDLE, rendered=VK_NULL_HANDLE;
    VkFence frameFence=VK_NULL_HANDLE;

    bool check(VkResult r,const char* what){ if(r==VK_SUCCESS)return true; LOGE("%s failed: %d",what,(int)r); return false; }
    std::vector<char> readSpv(const char* path){
        std::ifstream f(path,std::ios::binary);
        if(!f){LOGE("Shader missing: %s",path);return {};}
        return std::vector<char>((std::istreambuf_iterator<char>(f)),{});
    }
    VkShaderModule shader(const char* path){
        auto bytes=readSpv(path); if(bytes.empty()||bytes.size()%4)return VK_NULL_HANDLE;
        VkShaderModuleCreateInfo ci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        ci.codeSize=bytes.size(); ci.pCode=reinterpret_cast<const uint32_t*>(bytes.data());
        VkShaderModule m=VK_NULL_HANDLE; if(!check(vkCreateShaderModule(device,&ci,nullptr,&m),"create shader"))return VK_NULL_HANDLE; return m;
    }
    bool init(){
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.pApplicationName="Colombiria Graphics Engine"; app.applicationVersion=VK_MAKE_VERSION(0,3,0);
        app.pEngineName="Colombiria Vulkan"; app.engineVersion=VK_MAKE_VERSION(0,1,0); app.apiVersion=VK_API_VERSION_1_1;
        const char* extensions[]={VK_KHR_SURFACE_EXTENSION_NAME,VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; ici.pApplicationInfo=&app;
        ici.enabledExtensionCount=2; ici.ppEnabledExtensionNames=extensions;
        if(!check(vkCreateInstance(&ici,nullptr,&instance),"create instance"))return false;
        VkAndroidSurfaceCreateInfoKHR sci{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR}; sci.window=window;
        if(!check(vkCreateAndroidSurfaceKHR(instance,&sci,nullptr,&surface),"create Android surface"))return false;
        uint32_t count=0; vkEnumeratePhysicalDevices(instance,&count,nullptr);
        if(!count){LOGE("No Vulkan physical device");return false;}
        std::vector<VkPhysicalDevice> devices(count); vkEnumeratePhysicalDevices(instance,&count,devices.data());
        for(auto candidate:devices){
            VkPhysicalDeviceProperties candidateProps{}; vkGetPhysicalDeviceProperties(candidate,&candidateProps);
            if(candidateProps.apiVersion < VK_API_VERSION_1_1) continue;
            uint32_t qcount=0; vkGetPhysicalDeviceQueueFamilyProperties(candidate,&qcount,nullptr);
            std::vector<VkQueueFamilyProperties> qs(qcount); vkGetPhysicalDeviceQueueFamilyProperties(candidate,&qcount,qs.data());
            for(uint32_t i=0;i<qcount;i++){VkBool32 present=VK_FALSE;vkGetPhysicalDeviceSurfaceSupportKHR(candidate,i,surface,&present);
                if((qs[i].queueFlags&VK_QUEUE_GRAPHICS_BIT)&&present){gpu=candidate;queueFamily=i;break;}}
            if(gpu)break;
        }
        if(!gpu){LOGE("No Vulkan 1.1+ graphics/present device found");return false;}
        VkPhysicalDeviceProperties props{};vkGetPhysicalDeviceProperties(gpu,&props);
        LOGI("GPU: %s Vulkan %u.%u.%u",props.deviceName,VK_VERSION_MAJOR(props.apiVersion),VK_VERSION_MINOR(props.apiVersion),VK_VERSION_PATCH(props.apiVersion));
        const char* devExt[]={VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        float priority=1.f; VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        qci.queueFamilyIndex=queueFamily;qci.queueCount=1;qci.pQueuePriorities=&priority;
        VkPhysicalDeviceFeatures features{};
        VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};dci.queueCreateInfoCount=1;dci.pQueueCreateInfos=&qci;
        dci.enabledExtensionCount=1;dci.ppEnabledExtensionNames=devExt;dci.pEnabledFeatures=&features;
        if(!check(vkCreateDevice(gpu,&dci,nullptr,&device),"create device"))return false;
        vkGetDeviceQueue(device,queueFamily,0,&queue);
        if(!createSwapchain())return false;
        return createPipeline();
    }
    bool createSwapchain(){
        VkSurfaceCapabilitiesKHR caps{}; if(!check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu,surface,&caps),"surface caps"))return false;
        uint32_t n=0;vkGetPhysicalDeviceSurfaceFormatsKHR(gpu,surface,&n,nullptr);if(!n)return false;
        std::vector<VkSurfaceFormatKHR> formats(n);vkGetPhysicalDeviceSurfaceFormatsKHR(gpu,surface,&n,formats.data());
        VkSurfaceFormatKHR chosen=formats[0];
        for(auto f:formats)if(f.format==VK_FORMAT_B8G8R8A8_UNORM){chosen=f;break;}
        format=chosen.format;
        extent=caps.currentExtent;
        if(extent.width==UINT32_MAX){extent.width=(uint32_t)ANativeWindow_getWidth(window);extent.height=(uint32_t)ANativeWindow_getHeight(window);}
        uint32_t imageCount=std::max(caps.minImageCount+1,2u);if(caps.maxImageCount&&imageCount>caps.maxImageCount)imageCount=caps.maxImageCount;
        VkSwapchainCreateInfoKHR ci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        ci.surface=surface;ci.minImageCount=imageCount;ci.imageFormat=format;ci.imageColorSpace=chosen.colorSpace;ci.imageExtent=extent;
        ci.imageArrayLayers=1;ci.imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;ci.imageSharingMode=VK_SHARING_MODE_EXCLUSIVE;
        ci.preTransform=caps.currentTransform;ci.compositeAlpha=VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;ci.presentMode=VK_PRESENT_MODE_FIFO_KHR;ci.clipped=VK_TRUE;
        if(!check(vkCreateSwapchainKHR(device,&ci,nullptr,&swapchain),"create swapchain"))return false;
        vkGetSwapchainImagesKHR(device,swapchain,&imageCount,nullptr);images.resize(imageCount);vkGetSwapchainImagesKHR(device,swapchain,&imageCount,images.data());
        views.resize(imageCount);
        for(size_t i=0;i<images.size();i++){VkImageViewCreateInfo vi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};vi.image=images[i];vi.viewType=VK_IMAGE_VIEW_TYPE_2D;vi.format=format;
            vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};if(!check(vkCreateImageView(device,&vi,nullptr,&views[i]),"create image view"))return false;}
        VkAttachmentDescription color{};color.format=format;color.samples=VK_SAMPLE_COUNT_1_BIT;color.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR;color.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
        color.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;color.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;color.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;color.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription sub{};sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;sub.colorAttachmentCount=1;sub.pColorAttachments=&ref;
        VkRenderPassCreateInfo rp{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};rp.attachmentCount=1;rp.pAttachments=&color;rp.subpassCount=1;rp.pSubpasses=&sub;
        if(!check(vkCreateRenderPass(device,&rp,nullptr,&renderPass),"create render pass"))return false;
        framebuffers.resize(views.size());
        for(size_t i=0;i<views.size();i++){VkFramebufferCreateInfo fi{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};fi.renderPass=renderPass;fi.attachmentCount=1;fi.pAttachments=&views[i];fi.width=extent.width;fi.height=extent.height;fi.layers=1;
            if(!check(vkCreateFramebuffer(device,&fi,nullptr,&framebuffers[i]),"create framebuffer"))return false;}
        VkCommandPoolCreateInfo pci{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};pci.queueFamilyIndex=queueFamily;pci.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        if(!check(vkCreateCommandPool(device,&pci,nullptr,&commandPool),"create command pool"))return false;
        commands.resize(images.size());VkCommandBufferAllocateInfo ai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ai.commandPool=commandPool;ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ai.commandBufferCount=(uint32_t)commands.size();
        if(!check(vkAllocateCommandBuffers(device,&ai,commands.data()),"allocate command buffers"))return false;
        VkSemaphoreCreateInfo si{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};if(!check(vkCreateSemaphore(device,&si,nullptr,&acquired),"create acquire semaphore"))return false;
        if(!check(vkCreateSemaphore(device,&si,nullptr,&rendered),"create render semaphore"))return false;
        VkFenceCreateInfo fci{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};fci.flags=VK_FENCE_CREATE_SIGNALED_BIT;
        if(!check(vkCreateFence(device,&fci,nullptr,&frameFence),"create fence"))return false;
        return true;
    }
    bool createPipeline(){
        VkShaderModule vert=shader(COLOMBIRIA_VERT_SPV),frag=shader(COLOMBIRIA_FRAG_SPV);if(!vert||!frag)return false;
        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0]={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT;stages[0].module=vert;stages[0].pName="main";
        stages[1]={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT;stages[1].module=frag;stages[1].pName="main";
        VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        VkPipelineInputAssemblyStateCreateInfo ia{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo vp{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};vp.viewportCount=1;vp.scissorCount=1;
        VkPipelineRasterizationStateCreateInfo rs{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};rs.polygonMode=VK_POLYGON_MODE_FILL;rs.cullMode=VK_CULL_MODE_NONE;rs.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;rs.lineWidth=1.f;
        VkPipelineMultisampleStateCreateInfo ms{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState blend{};blend.colorWriteMask=0xf;
        VkPipelineColorBlendStateCreateInfo cb{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};cb.attachmentCount=1;cb.pAttachments=&blend;
        VkDynamicState dyns[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};VkPipelineDynamicStateCreateInfo dyn{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};dyn.dynamicStateCount=2;dyn.pDynamicStates=dyns;
        VkPipelineLayoutCreateInfo li{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};if(!check(vkCreatePipelineLayout(device,&li,nullptr,&pipelineLayout),"pipeline layout"))return false;
        VkGraphicsPipelineCreateInfo pi{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};pi.stageCount=2;pi.pStages=stages;pi.pVertexInputState=&vi;pi.pInputAssemblyState=&ia;pi.pViewportState=&vp;pi.pRasterizationState=&rs;pi.pMultisampleState=&ms;pi.pColorBlendState=&cb;pi.pDynamicState=&dyn;pi.layout=pipelineLayout;pi.renderPass=renderPass;pi.subpass=0;
        VkResult result=vkCreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&pipeline);
        vkDestroyShaderModule(device,vert,nullptr);vkDestroyShaderModule(device,frag,nullptr);
        return check(result,"graphics pipeline");
    }
    void draw(){
        if(!running)return;
        vkWaitForFences(device,1,&frameFence,VK_TRUE,UINT64_MAX);vkResetFences(device,1,&frameFence);
        uint32_t index=0;VkResult a=vkAcquireNextImageKHR(device,swapchain,UINT64_MAX,acquired,VK_NULL_HANDLE,&index);
        if(a!=VK_SUCCESS&&a!=VK_SUBOPTIMAL_KHR){running=false;return;}
        VkCommandBuffer cmd=commands[index];vkResetCommandBuffer(cmd,0);
        VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};vkBeginCommandBuffer(cmd,&bi);
        VkClearValue clear{};clear.color={{0.30f,0.48f,0.58f,1.f}};
        VkRenderPassBeginInfo ri{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};ri.renderPass=renderPass;ri.framebuffer=framebuffers[index];ri.renderArea.extent=extent;ri.clearValueCount=1;ri.pClearValues=&clear;
        vkCmdBeginRenderPass(cmd,&ri,VK_SUBPASS_CONTENTS_INLINE);
        VkViewport viewport{0.f,0.f,(float)extent.width,(float)extent.height,0.f,1.f};VkRect2D scissor{{0,0},extent};
        vkCmdSetViewport(cmd,0,1,&viewport);vkCmdSetScissor(cmd,0,1,&scissor);vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
        vkCmdDraw(cmd,3,1,0,0);vkCmdEndRenderPass(cmd);vkEndCommandBuffer(cmd);
        VkPipelineStageFlags waitStage=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.waitSemaphoreCount=1;submit.pWaitSemaphores=&acquired;submit.pWaitDstStageMask=&waitStage;submit.commandBufferCount=1;submit.pCommandBuffers=&cmd;submit.signalSemaphoreCount=1;submit.pSignalSemaphores=&rendered;
        if(!check(vkQueueSubmit(queue,1,&submit,frameFence),"queue submit")){running=false;return;}
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};present.waitSemaphoreCount=1;present.pWaitSemaphores=&rendered;present.swapchainCount=1;present.pSwapchains=&swapchain;present.pImageIndices=&index;
        VkResult p=vkQueuePresentKHR(queue,&present);if(p!=VK_SUCCESS&&p!=VK_SUBOPTIMAL_KHR){LOGE("present: %d",p);running=false;}
    }
    void cleanup(){
        if(device)vkDeviceWaitIdle(device);
        if(device){if(frameFence)vkDestroyFence(device,frameFence,nullptr);if(acquired)vkDestroySemaphore(device,acquired,nullptr);if(rendered)vkDestroySemaphore(device,rendered,nullptr);
            for(auto f:framebuffers)vkDestroyFramebuffer(device,f,nullptr);if(pipeline)vkDestroyPipeline(device,pipeline,nullptr);if(pipelineLayout)vkDestroyPipelineLayout(device,pipelineLayout,nullptr);
            if(renderPass)vkDestroyRenderPass(device,renderPass,nullptr);if(commandPool)vkDestroyCommandPool(device,commandPool,nullptr);for(auto v:views)vkDestroyImageView(device,v,nullptr);
            if(swapchain)vkDestroySwapchainKHR(device,swapchain,nullptr);vkDestroyDevice(device,nullptr);}
        if(instance&&surface)vkDestroySurfaceKHR(instance,surface,nullptr);if(instance)vkDestroyInstance(instance,nullptr);
    }
};
Engine* engine=nullptr;
}
extern "C" JNIEXPORT void JNICALL Java_com_colombiria_game_MainActivity_nativeStart(JNIEnv* env,jobject,jobject jsurface){
    if(engine)return;engine=new Engine();engine->window=ANativeWindow_fromSurface(env,jsurface);
    engine->running=true;Engine* e=engine;
    e->thread=std::thread([e](){if(e->init()){LOGI("Vulkan renderer started");while(e->running) e->draw();}else LOGE("Vulkan initialization failed");e->cleanup();if(e->window)ANativeWindow_release(e->window);});
}
extern "C" JNIEXPORT void JNICALL Java_com_colombiria_game_MainActivity_nativeStop(JNIEnv*,jobject){
    if(!engine)return;engine->running=false;if(engine->thread.joinable())engine->thread.join();delete engine;engine=nullptr;
}
extern "C" JNIEXPORT void JNICALL Java_com_colombiria_game_MainActivity_nativeSetThrottle(JNIEnv*,jobject,jboolean enabled){if(engine)engine->throttle=enabled;}
extern "C" JNIEXPORT void JNICALL Java_com_colombiria_game_MainActivity_nativeSetSteering(JNIEnv*,jobject,jfloat value){if(engine)engine->steering=value;}
