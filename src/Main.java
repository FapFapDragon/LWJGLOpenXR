import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import java.math.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL31C.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    GLFWErrorCallback errorCallback;
    GLFWKeyCallback keyCallback;
    GLFWFramebufferSizeCallback fbCallback;

    long window;
    int width = 640;
    int height = 480;
    Object lock = new Object();
    boolean destroyed;

    double interpolation = 0;
    final int TICKS_PER_SECOND = 25;
    final int SKIP_TICKS = 1000 / TICKS_PER_SECOND;
    final int MAX_FRAMESKIP = 5;

    private float speed = 0.3f; // 3 units / second
    private float mouseSpeed = 0.005f;

    private  Vector3f position = new Vector3f(0, 0, 0f);

    private Matrix4f view_matrix = new Matrix4f();

    private Matrix4f perspective_matrix = new Matrix4f();

    private Matrix4f vp_matrix = new Matrix4f();

    private Vector3f right = new Vector3f();

    private Vector3f direction = new Vector3f();

    private Vector3f up = new Vector3f();
    float horizontalAngle = 0;

    float verticalAngle = 0;


    private Input input;
    Shader default_shader;
    Triangle tri;

    Square sqr;

    /*
     init: Initialize GLFW, openGL, And any objects to be drawn
     inputs:     None
     returns:    True or false depending on whether or not everything falls apart
    */
    private boolean init()
    {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
        {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_SAMPLES, 4); // 4x antialiasing
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); // We want OpenGL 3.3
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE); // To make MacOS happy; should not be needed
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); // We don't want the old OpenGL



        window = glfwCreateWindow(width, height, "JavaOpenGLXR", NULL, NULL);

        if (window == NULL)
        {
            throw new RuntimeException("Unable to Create window");
        }

        input = new Input();
        glfwSetKeyCallback(window, input);


        glfwMakeContextCurrent(window);

        GL.createCapabilities();

        String vertShaderPath = "C:\\Users\\Troy\\Documents\\GitHub\\LWJGLOpenGLSample\\src\\shaders\\vert.vsh";
        String fragShaderPath = "C:\\Users\\Troy\\Documents\\GitHub\\LWJGLOpenGLSample\\src\\shaders\\frag.fg";
        try {
            default_shader = new Shader(vertShaderPath, fragShaderPath, true);
        }
        catch (Exception e){
            System.err.println("Unable to load shader");
        }

        tri  = new Triangle();

        sqr = new Square();

        perspective_matrix.setPerspective(45, width/height, 0.1f, 100);
        view_matrix.lookAt(new Vector3f(0, 0, 1f), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        perspective_matrix.mul(view_matrix, vp_matrix);
        return true;
    }

    /*
        run: The function called to kick off the program
        inputs:     None
        returns:    None
    */
    private void run()
    {
        init();

        loop();

        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        return;
    }

    /*
        Loop: main program loop
        inputs:     None
        returns:    None
    */
    private void loop()
    {

        double next_game_tick = System.currentTimeMillis();
        int loops;

        // Set the clear color
        glClearColor(0f, 0.0f, 0.0f, 0.0f);
        glEnable(GL_CULL_FACE);
        // Ensure we can capture the escape key being pressed below
        glfwSetInputMode(window, GLFW_STICKY_KEYS, GL_TRUE);

        perspective_matrix.mul(view_matrix, vp_matrix);
        //Loop gets updated 60 times per second (locked to 60FPS)
        do{
            loops = 0;
            do {
                checkKeys();
                checkMouse();
                next_game_tick += SKIP_TICKS;
                loops++;
            } while (System.currentTimeMillis() > next_game_tick && loops < MAX_FRAMESKIP);

            interpolation = (System.currentTimeMillis() + SKIP_TICKS - next_game_tick / (double) SKIP_TICKS);
            drawThings();
        } // Check if the ESC key was pressed or the window was closed
        while( glfwGetKey(window, GLFW_KEY_ESCAPE ) != GLFW_PRESS && glfwWindowShouldClose(window) == false );
    }

    /*
        Draw Things: All draw calls for openGL and otherwise are processed here
        inputs:     None
        returns:    None
    */
    private void drawThings()
    {
        // Clear the screen. It's not mentioned before Tutorial 02, but it can cause flickering, so it's there nonetheless.
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        //tri.draw(vp_matrix);
        sqr.draw(vp_matrix);
        // Swap buffers
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    /*
        Check Keys: a function to check whether up down left or right arrow keys are being pressed
        inputs:     None
        returns:    None
    */
    private void checkKeys()
    {
        Vector3f temp = new Vector3f();
        if (input.isKeyDown(GLFW_KEY_UP) && !input.isKeyDown(GLFW_KEY_DOWN))
        {
            direction.mul(speed, temp);
            position.add(temp);
            calculateViewProjection();
        }
        else if (input.isKeyDown(GLFW_KEY_DOWN) && !input.isKeyDown(GLFW_KEY_UP))
        {
            direction.mul(speed, temp);
            position.sub(temp);
            calculateViewProjection();
        }
        if (input.isKeyDown(GLFW_KEY_LEFT) && !input.isKeyDown(GLFW_KEY_RIGHT))
        {
            right.mul(speed, temp);
            position.sub(temp);
            calculateViewProjection();
        }
        else if (input.isKeyDown(GLFW_KEY_RIGHT)&& !input.isKeyDown(GLFW_KEY_LEFT))
        {
            right.mul(speed, temp);
            position.add(temp);
            calculateViewProjection();
        }

    }

    private void checkMouse()
    {
        double[] xpos = new double[2];
        double[] ypos = new double[2];

        glfwGetCursorPos(window, xpos, ypos);
        glfwSetCursorPos(window, width/2, height/2);
        horizontalAngle += mouseSpeed * (width/2 - xpos[0] );
        verticalAngle   += mouseSpeed *  (height/2 - ypos[0] );

        direction = new Vector3f( (float) (Math.cos(verticalAngle) * Math.sin(horizontalAngle)), (float) Math.sin(verticalAngle) , (float) (Math.cos(verticalAngle) * Math.cos(horizontalAngle)));

        right = new Vector3f((float)Math.sin(horizontalAngle - 3.14/2.0f) , 0, (float)Math.cos(horizontalAngle - 3.14/2.0f));

        right.cross(direction, up);

        calculateViewProjection();
    }


    private void calculateViewProjection()
    {
       //view_matrix = view_matrix.lookAt(direction, );
        Vector3f final_position = new Vector3f();
        position.add(direction, final_position);
        view_matrix = new Matrix4f().lookAt(position, final_position, new Vector3f(0, 1, 0));
        perspective_matrix.mul(view_matrix, vp_matrix);
    }

    public static void main(String[] args) {
        new Main().run();
    }
}