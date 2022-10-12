
//File Stuff
import java.io.IOException;
import java.lang.RuntimeException;
import java.nio.file.Files;
import java.nio.file.Path;

//OpenGL Stuff
import static org.lwjgl.opengl.GL31C.*;
public class Shader {

    private int program;

    public static Shader default_program;

    /*
     loadShaderText: read the text for a shader from a file into a string
     inputs:     String Path
     returns:    text contained within file
     throws:     IOException if the file cannot be opened
    */
    private String loadShaderText(String path) throws IOException
    {
        String str;
        try
        {
            Path fileName = Path.of(path);
            str = Files.readString(fileName);
        }
        catch (IOException e)
        {
            System.err.println("Unable to open shader file");
            throw new IOException("Couldn't open file: "+path);
        }

        return str;
    }

    /*
     compileShader: Compile a shader from a string
     inputs:     the pointer to the shader object, the text of the shader
     returns:    true or false depending on whether the shader compiles successfully
    */
    public boolean compileShader(int shader, String shader_text)
    {
        glShaderSource(shader, shader_text);
        glCompileShader(shader);

        int result = glGetShaderi(shader, GL_COMPILE_STATUS);

        if (result == 0)
        {
            String info = glGetShaderInfoLog(shader);
            System.err.println(info);
            return false;
        }

        return true;
    }

    /*
     Build Program: Builds shader program from vertex and fragment shaders
     inputs:     Pointer to vertex shader, pointer to fragment shader
     returns:    None
    */
    private void buildProgram(int VertShaderID, int FragShaderID)
    {
        this.program = glCreateProgram();
        glAttachShader(this.program, VertShaderID);
        glAttachShader(this.program, FragShaderID);
        glLinkProgram(this.program);

        int result = glGetProgrami(this.program, GL_LINK_STATUS);
        if (result == 0)
        {
            String info = glGetProgramInfoLog(this.program);
            throw new RuntimeException(info);
        }
        glDetachShader(this.program, VertShaderID);
        glDetachShader(this.program, FragShaderID);

        glDeleteShader(VertShaderID);
        glDeleteShader(FragShaderID);

    }

    /*
     getProgram: get the shader program pointer
     inputs:     None
     returns:    program pointer (as an int)
    */
    public int getProgram()
    {
        return this.program;
    }

    /*
     Constructor: Run when shader is created
     inputs:     the path to the vertex shader, the path to the fragment shader
     returns:    None
     throws:     IOException if vert or frag shader can't be loaded from file, RuntimeException if shaders can't compile
    */
    public Shader(String vertex_path,String fragment_path) throws IOException, RuntimeException
    {
        int VertShaderID = glCreateShader(GL_VERTEX_SHADER);
        int FragShaderID = glCreateShader(GL_FRAGMENT_SHADER);

        String vert = loadShaderText(vertex_path);
        String frag = loadShaderText(fragment_path);

        compileShader(VertShaderID ,vert);
        compileShader(FragShaderID, frag);

        buildProgram(VertShaderID, FragShaderID);
    }

    /*
     Constructor: Run when shader is created
     inputs:     the path to the vertex shader, the path to the fragment shader, whether to set this shader as default
     returns:    None
     throws:     IOException if vert or frag shader can't be loaded from file, RuntimeException if shaders can't compile
    */
    public Shader(String vertex_path,String fragment_path, boolean default_shader) throws IOException, RuntimeException
    {
        int VertShaderID = glCreateShader(GL_VERTEX_SHADER);
        int FragShaderID = glCreateShader(GL_FRAGMENT_SHADER);

        String vert = loadShaderText(vertex_path);
        String frag = loadShaderText(fragment_path);

        if (!compileShader(VertShaderID ,vert))
        {
            throw new RuntimeException("Unable to compile vertex shader");
        }
        if (!compileShader(FragShaderID ,frag))
        {
            throw new RuntimeException("Unable to compile fragment shader");
        }

        buildProgram(VertShaderID, FragShaderID);

        if (default_shader)
        {
            Shader.default_program = this;
        }
    }
}
