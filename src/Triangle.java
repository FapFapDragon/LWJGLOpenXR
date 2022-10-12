import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ARBVertexBufferObject;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL31C.*;
import org.lwjgl.system.MemoryStack;

public class Triangle {

    //Values needed for Model Matrix
    private Vector3f position    = new Vector3f(1, 1, 1);
    private Quaternionf rotation = new Quaternionf(0f, 0f, 0f, 1f);
    private Vector3f scale       = new Vector3f(1f, 1f, 1f);

    private Matrix4f model_matrix = new Matrix4f();



    private Matrix4f mvp;
    private float vertices[] = new float[]{-1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            0.0f,  1.0f, 0.0f};

    private int vao;
    private int vbo;

    private int mvp_location;

    private Shader shader;

    public Triangle(Shader shader)
    {
        initVAO();
        initVBO();
        this.shader = shader;
        return;
    }

    public Triangle()
    {
        initVAO();
        initVBO();
        this.shader = Shader.default_program;
        this.mvp_location = glGetUniformLocation(this.shader.getProgram(), "mvp");

        //Create the model matrix
        model_matrix = new Matrix4f().translationRotateScale(position, rotation, scale);
    }

    private void initVBO()
    {
        vbo = ARBVertexBufferObject.glGenBuffersARB();
        ARBVertexBufferObject.glBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, vbo);
        ARBVertexBufferObject.glBufferDataARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, vertices, ARBVertexBufferObject.GL_STATIC_DRAW_ARB);
    }
    private void initVAO()
    {
        vao = ARBVertexArrayObject.glGenVertexArrays();
        ARBVertexArrayObject.glBindVertexArray(vao);
    }

    public void draw(Matrix4f vp_matrix)
    {
        mvp = new Matrix4f();
        vp_matrix.mul(model_matrix, mvp);
        try( MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(this.mvp_location, false, mvp.get(stack.mallocFloat(16)));

        }
        glUseProgram(shader.getProgram());
        ARBVertexArrayObject.glBindVertexArray(vao);
        ARBVertexBufferObject.glBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, vbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisableVertexAttribArray(0);
    }
}
