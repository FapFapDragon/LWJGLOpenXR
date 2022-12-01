package LWJGLOpenXRSample.openxr;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import LWJGLOpenXRSample.Square;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.glfw.*;

//Native Windows Commands
import org.lwjgl.system.Pointer;
import org.lwjgl.system.windows.RECT;
import org.lwjgl.system.windows.User32;

import java.awt.*;
import java.io.Console;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class XrProgram {
    private String application_name;

    private long window;

    public XrInstance instance;

    public XrSession session;

    public XrGraphicsBindingOpenGLWin32KHR graphics_binding;

    public XrSpace reference_space;

    public int view_type = XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

    public int state = XR10.XR_SESSION_STATE_UNKNOWN;

    public long system_id;

    public float near_z;

    public float far_z;

    boolean xr_shutdown = false;

    ArrayList<ArrayList<Integer>> frame_buffers;

    public int swapchain_format;

    public int depth_swapchain_format;

    public ArrayList<ArrayList<XrSwapchainImageOpenGLKHR>> images;

    public ArrayList<ArrayList<XrSwapchainImageOpenGLKHR>> depth_images;

    public ArrayList<XrSwapchain> swapchains;

    public ArrayList<XrSwapchain> depth_swapchains;

    public ArrayList<XrCompositionLayerProjectionView> projection_views;

    public ArrayList<XrViewConfigurationView> xr_config_views;

    boolean depth_supported = false;

    public ArrayList<XrCompositionLayerDepthInfoKHR> depth_info;

    public Square square_to_draw;

    public String[] required_extensions = { KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME };

    public String[] optional_extensions = { KHRCompositionLayerDepth.XR_KHR_COMPOSITION_LAYER_DEPTH_EXTENSION_NAME };

    public Vector<String> enabled_extensions = new Vector<String>();

    public XrProgram(String application_name, long window)
    {
        this.window = window;
        this.application_name = application_name;
        this.near_z = 0.01f;
        this.far_z  = 100.0f;
    }

    public boolean init()
    {
        if (!this.createInstance())
        {

            return false;
        }

        if (!this.createSession())
        {

            return false;
        }

        if (!this.createReferenceSpace())
        {
            return false;
        }

        if (!this.createViews())
        {
            return false;
        }

        return true;
    }

    public boolean createSession()
    {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            XrSystemGetInfo system_get_info = XrSystemGetInfo.calloc(stack);
            system_get_info.type(XR10.XR_TYPE_SYSTEM_GET_INFO);
            system_get_info.formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);

            LongBuffer system_id = stack.mallocLong(1);

            if (!checkXrResult(XR10.xrGetSystem(this.instance, system_get_info, system_id)))
            {
                System.out.println("unable to get System ID");
                return false;
            }
            this.system_id = system_id.get(0);

            XrGraphicsRequirementsOpenGLKHR graphics_requirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack);
            graphics_requirements.type(KHROpenGLEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);

            PointerBuffer graphics_pointer = stack.callocPointer(1);

            //This has to be called before we do anything with the session otherwise you get validation layer errors
            if (!checkXrResult(KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(this.instance, this.system_id, graphics_requirements)))
            {
                System.out.println("unable to get System information");
                return false;
            }

            XrGraphicsBindingOpenGLWin32KHR graphics_binding = XrGraphicsBindingOpenGLWin32KHR.calloc(stack);

            graphics_binding.type(KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_WIN32_KHR);
            graphics_binding.next(NULL);
            graphics_binding.hGLRC(GLFWNativeWGL.glfwGetWGLContext(window));
            graphics_binding.hDC(User32.GetDC(GLFWNativeWin32.glfwGetWin32Window(window)));

            XrSessionCreateInfo session_create_info = XrSessionCreateInfo.calloc(stack);
            session_create_info.type(XR10.XR_TYPE_SESSION_CREATE_INFO);
            session_create_info.next(NULL);
            session_create_info.createFlags(0);
            session_create_info.next(graphics_binding);
            session_create_info.systemId(this.system_id);

            PointerBuffer session_pointer = stack.callocPointer(1);
            if (!checkXrResult(XR10.xrCreateSession(this.instance, session_create_info, session_pointer)))
            {
                System.out.println("Unable to create Session");
                return false;
            }
            this.session = new XrSession(session_pointer.get(0), this.instance);
        }
        return true;
    }

    public boolean createInstance()
    {
        //XrSystemGetInfo system_get_info = new XrSystemGetInfo();
        //system_get_info.next(0);
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrApplicationInfo app_info = XrApplicationInfo.calloc(stack);
            app_info.engineName(stack.UTF8Safe("Intelij"));
            app_info.engineVersion(1);
            app_info.apiVersion(XR10.XR_CURRENT_API_VERSION);
            app_info.applicationVersion(1);
            app_info.applicationName(stack.UTF8Safe("LWJGL OpenXR Program"));

            if (!checkExtensionSupport())
            {
                return false;
            }

            XrInstanceCreateInfo instance_create_info = XrInstanceCreateInfo.calloc(stack);
            instance_create_info.type(XR10.XR_TYPE_INSTANCE_CREATE_INFO);
            instance_create_info.applicationInfo(app_info);
            instance_create_info.enabledApiLayerNames((PointerBuffer) null);

            PointerBuffer enabled_extension_names = stack.mallocPointer(this.enabled_extensions.size());
            for (int i = 0; i < this.enabled_extensions.size(); i++)
            {
                enabled_extension_names.position(i);
                enabled_extension_names.put(stack.UTF8Safe(this.enabled_extensions.get(i)));
            }
            enabled_extension_names.flip();
            instance_create_info.enabledExtensionNames(enabled_extension_names);
            instance_create_info.createFlags(0);

            PointerBuffer instance = stack.callocPointer(1);
            if (!checkXrResult(XR10.xrCreateInstance(instance_create_info, instance)))
            {
                return false;
            }
            this.instance = new XrInstance(instance.get(0), instance_create_info);
            XRCapabilities caps = this.instance.getCapabilities();

        }
        return true;
    }

    public boolean checkExtensionSupport()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer extension_count = stack.mallocInt(1);
            if (!checkXrResult(XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, extension_count, null)))
            {
                return false;
            }

            XrExtensionProperties.Buffer properties = new XrExtensionProperties.Buffer(allocateStruct(extension_count.get(0), XrExtensionProperties.SIZEOF, XR10.XR_TYPE_EXTENSION_PROPERTIES, stack));//XrExtensionProperties.malloc(extension_count.get(0), stack);

            if (!checkXrResult(XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, extension_count, properties)))
            {
                return false;
            }

            boolean match = false;
            for (int i = 0; i < properties.capacity(); i++)
            {
                //System.out.println(properties.get(0).extensionName().array());
                XrExtensionProperties prop = properties.get(i);
                System.out.println(prop.extensionNameString());
                for (String str : required_extensions)
                {
                    if (str.equals(prop.extensionNameString()))
                    {
                        match = true;
                        enabled_extensions.add(prop.extensionNameString());
                    }
                }

                for (String str : optional_extensions)
                {
                    if (str.equals(prop.extensionNameString()))
                    {
                        this.depth_supported = true;
                        enabled_extensions.add(prop.extensionNameString());
                    }
                }
            }
            if (!match)
            {
                return false;
            }
        }
        return true;
    }

    public boolean createReferenceSpace()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrPosef pose = XrPosef.calloc();
            XrQuaternionf rot = XrQuaternionf.calloc(stack);
            rot.set(0, 0, 0, 1);
            XrVector3f pos = XrVector3f.calloc(stack);
            pos.set(0, 0, 0);

            pose.set(rot, pos);

            XrReferenceSpaceCreateInfo reference_space_create_info = XrReferenceSpaceCreateInfo.calloc(stack);
            reference_space_create_info.type(XR10.XR_TYPE_REFERENCE_SPACE_CREATE_INFO);
            reference_space_create_info.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_LOCAL);
            reference_space_create_info.poseInReferenceSpace(pose);

            PointerBuffer reference_space_ptr = stack.callocPointer(1);
            if (!checkXrResult(XR10.xrCreateReferenceSpace(this.session, reference_space_create_info, reference_space_ptr)))
            {
                System.out.println("unable to create Reference Space");
                return false;
            }

            this.reference_space = new XrSpace(reference_space_ptr.get(0), this.session);
        }
        return true;
    }

    public boolean createViews()
    {
        if (!checkViewConfigs(this.view_type))
        {
            System.out.println("ViewType " + this.view_type + " Not supported");
            return false;
        }




        return true;
    }

    public boolean checkViewConfigs(int view_type)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer config_count = stack.mallocInt(1);

            if (!checkXrResult(XR10.xrEnumerateViewConfigurations(this.instance, this.system_id, config_count, null)))
            {
                System.out.println("couldn't Enumerate View Configs");
                return false;
            }

            IntBuffer view_configs = stack.callocInt(config_count.get(0));

            if (!checkXrResult(XR10.xrEnumerateViewConfigurations(this.instance, this.system_id, config_count, view_configs)))
            {
                System.out.println("couldn't Enumerate View Configs data");
                return false;
            }

            boolean config_found = false;
            while (view_configs.hasRemaining())
            {
                int view_config_type = view_configs.get();
                if (view_config_type == view_type)
                {
                    config_found = true;
                    break;
                }
            }

            if (!config_found)
            {
                System.out.println("View type not supported");
                return false;
            }
        }
        return true;
    }

    public ByteBuffer allocateStruct(int capacity, int struct_size, int struct_type, MemoryStack stack)
    {
        ByteBuffer struct_buffer = stack.malloc(capacity * struct_size);
        for (int i = 0; i < capacity; i++)
        {
            struct_buffer.position(i * struct_size);
            struct_buffer.putInt(struct_type);
        }
        struct_buffer.rewind();
        return struct_buffer;
    }

    public boolean checkXrResult(int XR_RESULT)
    {

        if (XR_RESULT != XR10.XR_SUCCESS)
        {
            System.out.println("Result: " + xrResultToString(XR_RESULT));
            return false;
        }
        return true;
    }

    public void destroy()
    {
        XR10.xrDestroySpace(this.reference_space);

        XR10.xrDestroySession(this.session);

        XR10.xrDestroyInstance(this.instance);
        return;
    }

    private String xrResultToString(int XR_RESULT)
    {
        switch (XR_RESULT)
        {
            case (XR10.XR_SUCCESS):
                return "XR_SUCCESS";
            case (XR10.XR_TIMEOUT_EXPIRED):
                return "XR_TIMEOUT_EXPIRED";
            case (XR10.XR_EVENT_UNAVAILABLE):
                return "XR_EVENT_UNAVAILABLE";
            case (XR10.XR_SESSION_NOT_FOCUSED):
                return "XR_SESSION_NOT_FOCUSED";
            case (XR10.XR_FRAME_DISCARDED):
                return "XR_FRAME_DISCARDED";
            case (XR10.XR_ERROR_VALIDATION_FAILURE):
                return "XR_ERROR_VALIDATION_FAILURE";
            case (XR10.XR_ERROR_RUNTIME_FAILURE):
                return "XR_ERROR_RUNTIME_FAILURE";
            case (XR10.XR_ERROR_OUT_OF_MEMORY):
                return "XR_ERROR_OUT_OF_MEMORY";
            case (XR10.XR_ERROR_API_VERSION_UNSUPPORTED):
                return "XR_ERROR_API_VERSION_UNSUPPORTED";
            case (XR10.XR_ERROR_INITIALIZATION_FAILED):
                return "XR_ERROR_INITIALIZATION_FAILED";
            case (XR10.XR_ERROR_FUNCTION_UNSUPPORTED):
                return "XR_ERROR_FUNCTION_UNSUPPORTED";
            case (XR10.XR_ERROR_FEATURE_UNSUPPORTED):
                return "XR_ERROR_FEATURE_UNSUPPORTED";
            case (XR10.XR_ERROR_EXTENSION_NOT_PRESENT):
                return "XR_ERROR_EXTENSION_NOT_PRESENT";
            case (XR10.XR_ERROR_LIMIT_REACHED):
                return "XR_ERROR_LIMIT_REACHED";
            case (XR10.XR_ERROR_SIZE_INSUFFICIENT):
                return "XR_ERROR_SIZE_INSUFFICIENT";
            case (XR10.XR_ERROR_HANDLE_INVALID):
                return "XR_ERROR_HANDLE_INVALID";
            case(XR10.XR_ERROR_INSTANCE_LOST):
                return "XR_ERROR_INSTANCE_LOST";
            case(XR10.XR_ERROR_SESSION_RUNNING):
                return "XR_ERROR_SESSION_RUNNING";
            case(XR10.XR_ERROR_SESSION_NOT_RUNNING):
                return "XR_ERROR_SESSION_NOT_RUNNING";
            case(XR10.XR_ERROR_SESSION_LOST):
                return "XR_ERROR_SESSION_LOST";
            case(XR10.XR_ERROR_SYSTEM_INVALID):
                return "XR_ERROR_SYSTEM_INVALID";
            case(XR10.XR_ERROR_PATH_INVALID):
                return "XR_ERROR_PATH_INVALID";
            case(XR10.XR_ERROR_PATH_COUNT_EXCEEDED):
                return "XR_ERROR_PATH_COUNT_EXCEEDED";
            case(XR10.XR_ERROR_PATH_FORMAT_INVALID):
                return "XR_ERROR_PATH_FORMAT_INVALID";
            case(XR10.XR_ERROR_PATH_UNSUPPORTED):
                return "XR_ERROR_PATH_UNSUPPORTED";
            case(XR10.XR_ERROR_LAYER_INVALID):
                return "XR_ERROR_LAYER_INVALID";
            case(XR10.XR_ERROR_LAYER_LIMIT_EXCEEDED):
                return "XR_ERROR_LAYER_LIMIT_EXCEEDED";
            case(XR10.XR_ERROR_SWAPCHAIN_RECT_INVALID):
                return "XR_ERROR_SWAPCHAIN_RECT_INVALID";
            case(XR10.XR_ERROR_SWAPCHAIN_FORMAT_UNSUPPORTED):
                return "XR_ERROR_SWAPCHAIN_FORMAT_UNSUPPORTED";
            case(XR10.XR_ERROR_ACTION_TYPE_MISMATCH):
                return "XR_ERROR_ACTION_TYPE_MISMATCH";
            case(XR10.XR_ERROR_SESSION_NOT_READY):
                return "XR_ERROR_SESSION_NOT_READY";
            case(XR10.XR_ERROR_SESSION_NOT_STOPPING):
                return "XR_ERROR_SESSION_NOT_STOPPING";
            case(XR10.XR_ERROR_TIME_INVALID):
                return "XR_ERROR_TIME_INVALID";
            case(XR10.XR_ERROR_REFERENCE_SPACE_UNSUPPORTED):
                return "XR_ERROR_REFERENCE_SPACE_UNSUPPORTED";
            case(XR10.XR_ERROR_FILE_ACCESS_ERROR):
                return "XR_ERROR_FILE_ACCESS_ERROR";
            case(XR10.XR_ERROR_FILE_CONTENTS_INVALID):
                return "XR_ERROR_FILE_CONTENTS_INVALID";
            case(XR10.XR_ERROR_FORM_FACTOR_UNSUPPORTED):
                return "XR_ERROR_FORM_FACTOR_UNSUPPORTED";
            case(XR10.XR_ERROR_FORM_FACTOR_UNAVAILABLE):
                return "XR_ERROR_FORM_FACTOR_UNAVAILABLE";
            case(XR10.XR_ERROR_API_LAYER_NOT_PRESENT):
                return "XR_ERROR_API_LAYER_NOT_PRESENT";
            case(XR10.XR_ERROR_CALL_ORDER_INVALID	):
                return "XR_ERROR_CALL_ORDER_INVALID";
            case (XR10.XR_ERROR_GRAPHICS_DEVICE_INVALID):
                return "eRROR_GRAPHICS_DEVICE_INVALID";
            case(XR10.XR_ERROR_POSE_INVALID):
                return "XR_ERROR_POSE_INVALID";
            case(XR10.XR_ERROR_INDEX_OUT_OF_RANGE):
                return "XR_ERROR_INDEX_OUT_OF_RANG";
            case(XR10.XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED):
                return "XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED";
            case(XR10.XR_ERROR_ENVIRONMENT_BLEND_MODE_UNSUPPORTED):
                return "XR_ERROR_ENVIRONMENT_BLEND_MODE_UNSUPPORTED";
            case(XR10.XR_ERROR_NAME_DUPLICATED):
                return "XR_ERROR_NAME_DUPLICATED";
            case(XR10.XR_ERROR_NAME_INVALID):
                return "XR_ERROR_NAME_INVALID";
            case(XR10.XR_ERROR_ACTIONSET_NOT_ATTACHED):
                return "XR_ERROR_ACTIONSET_NOT_ATTACHED";
            case(XR10.XR_ERROR_ACTIONSETS_ALREADY_ATTACHED):
                return "XR_ERROR_ACTIONSETS_ALREADY_ATTACHED";
            case(XR10.XR_ERROR_LOCALIZED_NAME_DUPLICATED):
                return "XR_ERROR_LOCALIZED_NAME_DUPLICATED";
            case(XR10.XR_ERROR_LOCALIZED_NAME_INVALID):
                return "XR_ERROR_LOCALIZED_NAME_INVALID";
            case(XR10.XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING):
                return "XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING";
            case(XR10.XR_ERROR_RUNTIME_UNAVAILABLE):
                return "XR_ERROR_RUNTIME_UNAVAILABLE";
            default:
                return "Unknown Error Code";

        }
    }


}
