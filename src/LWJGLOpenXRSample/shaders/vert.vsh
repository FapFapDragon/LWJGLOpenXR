#version 330 core
layout(location = 0) in vec3 vertexPosition_modelspace;
uniform mat4 mvp;
out vec3 frag_color;

void main(){
  //gl_Position.xyz = vertexPosition_modelspace;
  //gl_Position.w = 1.0;
  gl_Position = mvp * vec4(vertexPosition_modelspace, 1);
  frag_color = vertexPosition_modelspace;
}