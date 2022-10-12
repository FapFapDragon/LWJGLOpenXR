import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL31C.*;

public class Square {

    //Values needed for Model Matrix
    private Vector3f position    = new Vector3f(1, 1, 1);
    private Quaternionf rotation = new Quaternionf(0f, 0f, 0f, 1f);
    private Vector3f scale       = new Vector3f(1f, 1f, 1f);

    private Matrix4f model_matrix = new Matrix4f();

    //Vertices to make up square, for this program not bothering with index buffer objects
    private float vertices[] = new float[]{-1.0f,-1.0f,-1.0f, // triangle 1 : begin
            -1.0f,-1.0f, 1.0f,
            -1.0f, 1.0f, 1.0f, // triangle 1 : end
            1.0f, 1.0f,-1.0f, // triangle 2 : begin
            -1.0f,-1.0f,-1.0f,
            -1.0f, 1.0f,-1.0f, // triangle 2 : end
            1.0f,-1.0f, 1.0f,
            -1.0f,-1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f, 1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f,-1.0f,
            1.0f,-1.0f, 1.0f,
            -1.0f,-1.0f, 1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f, 1.0f, 1.0f,
            -1.0f,-1.0f, 1.0f,
            1.0f,-1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f, 1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f,-1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f, 1.0f,-1.0f,
            -1.0f, 1.0f,-1.0f,
            1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f,-1.0f,
            -1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 1.0f,
            1.0f,-1.0f, 1.0f
    };

    private int vao;
    private int vbo;

    private int mvp_location;

    private Shader shader;

    /*
     Constructor: Run when square is created
     inputs:     The shader program for this square
     returns:    None
    */
    public Square(Shader shader)
    {
        initVAO();
        initVBO();
        this.shader = shader;
        return;
    }

    /*
     Constructor: Run when Square is created, uses default shader since none is provided
     inputs:     None
     returns:    None
    */
    public Square()
    {
        initVAO();
        initVBO();
        this.shader = Shader.default_program;
        this.mvp_location = glGetUniformLocation(this.shader.getProgram(), "mvp");

        //Create the model matrix
        model_matrix = new Matrix4f().translationRotateScale(position, rotation, scale);
    }

    /*
     initVBO:    Initialize the Vertex Buffer Object
     inputs:     None
     returns:    None
    */
    private void initVBO()
    {
        vbo = ARBVertexBufferObject.glGenBuffersARB();
        ARBVertexBufferObject.glBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, vbo);
        ARBVertexBufferObject.glBufferDataARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, vertices, ARBVertexBufferObject.GL_STATIC_DRAW_ARB);
    }

    /*
     initVBO:    Initialize the Vertex Array Object
     inputs:     None
     returns:    None
    */
    private void initVAO()
    {
        vao = ARBVertexArrayObject.glGenVertexArrays();
        ARBVertexArrayObject.glBindVertexArray(vao);
    }


    /*
        initVBO:    Draw the Square
        inputs:     View Projection matrix for Camera
        returns:    None
       */
    public void draw(Matrix4f vp_matrix)
    {
        //Calculate out the Model View Projection Matrix
        Matrix4f mvp = new Matrix4f();
        vp_matrix.mul(model_matrix, mvp);

        //Pass MVP matrix to the Shader
        try( MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(this.mvp_location, false, mvp.get(stack.mallocFloat(16)));

        }

        //Draw the Square
        glUseProgram(shader.getProgram());
        ARBVertexArrayObject.glBindVertexArray(vao);
        ARBVertexBufferObject.glBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, vbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        glDrawArrays(GL_TRIANGLES, 0, 12*3);
        glDisableVertexAttribArray(0);
    }
}
