import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;

public class Input extends GLFWKeyCallback{

    // Keycode sate list, size set according to available number of keys
    public static boolean[] keys = new boolean[65536];

    // Main key callback
    // This functions needs to be wrapped in a class in order to be referenced
    public void invoke(long window, int key, int scancode, int action, int mods) {
        //System.out.println(key);
        keys[key] = action != GLFW.GLFW_RELEASE;
    }

    // Key down
    public static boolean isKeyDown(int keycode) {
        return keys[keycode];
    }

    public static boolean isKeyUp(int keycode)
    {
        return keys[keycode];
    }
}