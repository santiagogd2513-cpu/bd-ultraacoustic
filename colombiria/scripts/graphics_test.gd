extends Node3D
## Phase 1 visual acceptance scene: procedural Andean foothills, river and forest.

const WORLD_SIZE := 220.0
const GRID := 100
const TREE_COUNT := 850

func _ready() -> void:
	_build_environment()
	_build_terrain()
	_build_river()
	_build_forest()
	_build_test_vehicle()
	_build_camera()
	_build_sun()

func _build_environment() -> void:
	var world := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var sky_mat := ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color(0.12, 0.30, 0.52)
	sky_mat.sky_horizon_color = Color(0.72, 0.82, 0.88)
	sky_mat.ground_bottom_color = Color(0.10, 0.13, 0.12)
	sky_mat.ground_horizon_color = Color(0.42, 0.49, 0.46)
	sky.sky_material = sky_mat
	env.sky = sky
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_energy = 0.65
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.fog_enabled = true
	env.fog_light_color = Color(0.64, 0.73, 0.78)
	env.fog_density = 0.0018
	env.fog_sky_affect = 0.7
	world.environment = env
	add_child(world)

func _height(x: float, z: float) -> float:
	var broad := sin(x * 0.026 + 0.7) * 10.0 + cos(z * 0.021) * 8.0
	var ridges := sin((x + z) * 0.052) * 4.0 + cos((x - z) * 0.071) * 2.2
	var detail := sin(x * 0.17) * cos(z * 0.14) * 0.8
	var valley := -8.0 * exp(-pow((z - 18.0) / 15.0, 2.0))
	return broad + ridges + detail + valley

func _build_terrain() -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for z in range(GRID):
		for x in range(GRID):
			var x0 := (float(x) / GRID - 0.5) * WORLD_SIZE
			var x1 := (float(x + 1) / GRID - 0.5) * WORLD_SIZE
			var z0 := (float(z) / GRID - 0.5) * WORLD_SIZE
			var z1 := (float(z + 1) / GRID - 0.5) * WORLD_SIZE
			_add_triangle(st, Vector3(x0,_height(x0,z0),z0), Vector3(x1,_height(x1,z0),z0), Vector3(x0,_height(x0,z1),z1))
			_add_triangle(st, Vector3(x1,_height(x1,z0),z0), Vector3(x1,_height(x1,z1),z1), Vector3(x0,_height(x0,z1),z1))
	st.generate_normals()
	var terrain := MeshInstance3D.new()
	terrain.mesh = st.commit()
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.19, 0.29, 0.17)
	mat.roughness = 0.94
	terrain.material_override = mat
	add_child(terrain)

func _add_triangle(st: SurfaceTool, a: Vector3, b: Vector3, c: Vector3) -> void:
	st.set_uv(Vector2(a.x/24.0,a.z/24.0)); st.add_vertex(a)
	st.set_uv(Vector2(b.x/24.0,b.z/24.0)); st.add_vertex(b)
	st.set_uv(Vector2(c.x/24.0,c.z/24.0)); st.add_vertex(c)

func _build_river() -> void:
	var shader := Shader.new()
	shader.code = """
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_disabled;
uniform vec4 shallow_color : source_color = vec4(0.18, 0.48, 0.55, 0.88);
uniform vec4 deep_color : source_color = vec4(0.025, 0.16, 0.24, 0.96);
void fragment() {
	vec2 p = UV * 9.0;
	float wave = sin(p.x * 1.7 + TIME * 0.7) * cos(p.y * 1.2 - TIME * 0.56);
	float crest = smoothstep(0.48, 0.9, wave);
	ALBEDO = mix(deep_color.rgb, shallow_color.rgb, 0.35 + crest * 0.32);
	METALLIC = 0.28;
	ROUGHNESS = 0.16 + crest * 0.12;
	ALPHA = 0.94;
}
"""
	var water := MeshInstance3D.new()
	var plane := PlaneMesh.new()
	plane.size = Vector2(WORLD_SIZE, 19.0)
	water.mesh = plane
	water.position = Vector3(0, -5.0, 18.0)
	water.material_override = ShaderMaterial.new()
	water.material_override.shader = shader
	add_child(water)

func _build_forest() -> void:
	var trunk_mesh := CylinderMesh.new()
	trunk_mesh.top_radius = 0.16
	trunk_mesh.bottom_radius = 0.25
	trunk_mesh.height = 3.2
	var leaf_mesh := SphereMesh.new()
	leaf_mesh.radius = 1.55
	leaf_mesh.height = 3.7
	var trunks := MultiMesh.new()
	trunks.transform_format = MultiMesh.TRANSFORM_3D
	trunks.mesh = trunk_mesh
	trunks.instance_count = TREE_COUNT
	var leaves := MultiMesh.new()
	leaves.transform_format = MultiMesh.TRANSFORM_3D
	leaves.mesh = leaf_mesh
	leaves.instance_count = TREE_COUNT
	var rng := RandomNumberGenerator.new()
	rng.seed = 24819
	for i in range(TREE_COUNT):
		var x := rng.randf_range(-WORLD_SIZE*0.48,WORLD_SIZE*0.48)
		var z := rng.randf_range(-WORLD_SIZE*0.48,WORLD_SIZE*0.48)
		if absf(z-18.0) < 13.0:
			z += 19.0 if z > 18.0 else -19.0
		var y := _height(x,z)
		var scale := rng.randf_range(0.72,1.38)
		var basis := Basis(Vector3.UP,rng.randf_range(0.0,TAU)).scaled(Vector3.ONE*scale)
		trunks.set_instance_transform(i,Transform3D(basis,Vector3(x,y+1.6*scale,z)))
		leaves.set_instance_transform(i,Transform3D(basis,Vector3(x,y+4.0*scale,z)))
	var trunk_node := MultiMeshInstance3D.new()
	trunk_node.multimesh = trunks
	var bark := StandardMaterial3D.new()
	bark.albedo_color = Color(0.22,0.13,0.075)
	bark.roughness = 1.0
	trunk_node.material_override = bark
	add_child(trunk_node)
	var canopy := MultiMeshInstance3D.new()
	canopy.multimesh = leaves
	var foliage := StandardMaterial3D.new()
	foliage.albedo_color = Color(0.075,0.22,0.105)
	foliage.roughness = 0.88
	foliage.cull_mode = BaseMaterial3D.CULL_DISABLED
	canopy.material_override = foliage
	add_child(canopy)

func _build_test_vehicle() -> void:
	var vehicle := Node3D.new()
	vehicle.position = Vector3(0,_height(0,-36)+1.2,-36)
	var body := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = Vector3(2.2,0.85,4.3)
	body.mesh = box
	var paint := StandardMaterial3D.new()
	paint.albedo_color = Color(0.055,0.19,0.31)
	paint.metallic = 0.62
	paint.roughness = 0.27
	body.material_override = paint
	vehicle.add_child(body)
	var cabin := MeshInstance3D.new()
	var cab := BoxMesh.new()
	cab.size = Vector3(1.8,0.85,2.1)
	cabin.mesh = cab
	cabin.position = Vector3(0,0.78,-0.25)
	var glass := StandardMaterial3D.new()
	glass.albedo_color = Color(0.08,0.17,0.22)
	glass.metallic = 0.35
	glass.roughness = 0.12
	cabin.material_override = glass
	vehicle.add_child(cabin)
	add_child(vehicle)

func _build_camera() -> void:
	var camera := Camera3D.new()
	camera.look_at_from_position(Vector3(44,37,-58),Vector3(0,2,12),Vector3.UP)
	camera.fov = 58.0
	add_child(camera)
	camera.current = true

func _build_sun() -> void:
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-43,-28,0)
	sun.light_color = Color(1.0,0.91,0.78)
	sun.light_energy = 1.25
	sun.shadow_enabled = true
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	sun.directional_shadow_max_distance = 120.0
	add_child(sun)
