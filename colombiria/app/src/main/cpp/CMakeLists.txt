cmake_minimum_required(VERSION 3.22.1)
project(colombiria_vulkan LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
find_program(GLSLC glslc REQUIRED)
set(SHADER_OUT "${CMAKE_CURRENT_BINARY_DIR}/shaders")
file(MAKE_DIRECTORY "${SHADER_OUT}")
foreach(SHADER scene.vert scene.frag)
  get_filename_component(STAGE "${SHADER}" LAST_EXT)
  set(OUTPUT "${SHADER_OUT}/${SHADER}.spv")
  add_custom_command(OUTPUT "${OUTPUT}"
    COMMAND "${GLSLC}" "${CMAKE_CURRENT_SOURCE_DIR}/shaders/${SHADER}" -o "${OUTPUT}"
    DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/shaders/${SHADER}" VERBATIM)
  list(APPEND SHADER_BINARIES "${OUTPUT}")
endforeach()
add_custom_target(colombiria_shaders DEPENDS ${SHADER_BINARIES})
add_library(colombiria_vulkan SHARED vulkan_renderer.cpp)
add_dependencies(colombiria_vulkan colombiria_shaders)
target_include_directories(colombiria_vulkan PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_definitions(colombiria_vulkan PRIVATE
  COLOMBIRIA_VERT_SPV="${SHADER_OUT}/scene.vert.spv"
  COLOMBIRIA_FRAG_SPV="${SHADER_OUT}/scene.frag.spv")
target_link_libraries(colombiria_vulkan android log vulkan)
