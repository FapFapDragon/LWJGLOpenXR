package LWJGLOpenXRSample.openxr;

import LWJGLOpenXRSample.Main;
import LWJGLOpenXRSample.Square;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL40;
import org.lwjgl.openxr.*;
import LWJGLOpenXRSample.Square;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
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

    ArrayList<ArrayList<Integer>> frame_buffers = new ArrayList<ArrayList<Integer>>();

    public long swapchain_format;

    public long depth_swapchain_format;

    public ArrayList<XrSwapchainImageOpenGLKHR.Buffer> images = new ArrayList<XrSwapchainImageOpenGLKHR.Buffer>();

    public ArrayList<XrSwapchainImageOpenGLKHR.Buffer> depth_images = new ArrayList<XrSwapchainImageOpenGLKHR.Buffer>();

    public ArrayList<XrSwapchain> swapchains = new ArrayList<XrSwapchain>();;

    public ArrayList<XrSwapchain> depth_swapchains = new ArrayList<XrSwapchain>();

    public XrCompositionLayerProjectionView.Buffer projection_views;

    public XrViewConfigurationView.Buffer xr_config_views;

    boolean depth_supported = false;

    public ArrayList<XrCompositionLayerDepthInfoKHR> depth_info;

    public Square square_to_draw;

    public String[] required_extensions = { KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME };

    public String[] optional_extensions = { KHRCompositionLayerDepth.XR_KHR_COMPOSITION_LAYER_DEPTH_EXTENSION_NAME };

    public Vector<String> enabled_extensions = new Vector<String>();

    public Square square;

    public XrProgram(String application_name, long window)
    {
        this.window = window;
        this.application_name = application_name;
        this.near_z = 0.01f;
        this.far_z  = 100.0f;
    }

    public boolean init()
    {
        GL.createCapabilities();

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

        if (!this.beginSession())
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

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer view_count = stack.callocInt(1);

            if (!checkXrResult(XR10.xrEnumerateViewConfigurationViews(this.instance, this.system_id, this.view_type, view_count, null)))
            {
                System.out.println("Couldn't enumerate View Configuration Views");
                return false;
            }

            this.xr_config_views = new XrViewConfigurationView.Buffer(allocateStruct(view_count.get(0), XrViewConfigurationView.SIZEOF, XR10.XR_TYPE_VIEW_CONFIGURATION_VIEW));

            if (!checkXrResult(XR10.xrEnumerateViewConfigurationViews(this.instance, this.system_id, this.view_type, view_count, xr_config_views)))
            {
                System.out.println("Couldn't enumerate View Configuration Views data");
                return false;
            }

            if (!this.createSwapchains(view_count.get(0)))
            {
                System.out.println("Unable to create Swapchains");
                return false;
            }

            if (!this.getSwapchainImages())
            {
                System.out.println("Unable to populate swapchain images");
                return false;
            }

            // Currently this always returns true
            if (!this.genFrameBuffers())
            {
                System.out.println("Unable to gemerate Frame Buffers");
                return false;
            }

            this.projection_views = new XrCompositionLayerProjectionView.Buffer(allocateStruct(view_count.get(0), XrCompositionLayerProjectionView.SIZEOF, XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW));

            for (int i = 0; i < view_count.get(0); i++)
            {

                //XrCompositionLayerProjectionView projection_view = XrCompositionLayerProjectionView.calloc(stack);
                this.projection_views.get(i).type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW);
                this.projection_views.get(i).next(NULL);
                this.projection_views.get(i).subImage().swapchain(this.swapchains.get(i));
                this.projection_views.get(i).subImage().imageArrayIndex(0);
                this.projection_views.get(i).subImage().imageRect().offset().x(0);
                this.projection_views.get(i).subImage().imageRect().offset().y(0);
                this.projection_views.get(i).subImage().imageRect().extent().height(this.xr_config_views.get(i).recommendedImageRectHeight());
                this.projection_views.get(i).subImage().imageRect().extent().width(this.xr_config_views.get(i).recommendedImageRectWidth());
            }

            if (this.depth_swapchain_format != -1)
            {
                for (int i = 0; i < view_count.get(0); i++) {
                    XrCompositionLayerDepthInfoKHR depth_info = XrCompositionLayerDepthInfoKHR.malloc();
                    depth_info.subImage().swapchain(this.depth_swapchains.get(i));
                    depth_info.subImage().imageArrayIndex(0);
                    depth_info.subImage().imageRect().offset().x(0);
                    depth_info.subImage().imageRect().offset().y(0);
                    depth_info.subImage().imageRect().extent().height(this.xr_config_views.get(i).recommendedImageRectHeight());
                    depth_info.subImage().imageRect().extent().width(this.xr_config_views.get(i).recommendedImageRectWidth());
                    this.projection_views.get(i).next(depth_info);
                }
            }
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

    public boolean createSwapchains(int view_count)
    {
        int format = GL40.GL_SRGB8_ALPHA8;

        if(!checkSwapchainImageSupport(format))
        {
            System.out.println("Preferred Swapchain Image Format not Supported");
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = 0; i < view_count; i++) {

                XrSwapchainCreateInfo swapchain_create_info = XrSwapchainCreateInfo.calloc(stack);
                swapchain_create_info.type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO);
                swapchain_create_info.next(NULL);
                swapchain_create_info.createFlags(0);
                swapchain_create_info.usageFlags(XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR10.XR_SWAPCHAIN_USAGE_SAMPLED_BIT);
                swapchain_create_info.format(this.swapchain_format);
                swapchain_create_info.sampleCount(this.xr_config_views.get(i).recommendedSwapchainSampleCount());
                swapchain_create_info.width(this.xr_config_views.get(i).recommendedImageRectWidth());
                swapchain_create_info.height(this.xr_config_views.get(i).recommendedImageRectHeight());
                swapchain_create_info.faceCount(1);
                swapchain_create_info.arraySize(1);
                swapchain_create_info.mipCount(1);
                int width = this.xr_config_views.get(i).recommendedImageRectWidth();
                PointerBuffer swapchain = stack.callocPointer(1);
                if (!checkXrResult(XR10.xrCreateSwapchain(this.session, swapchain_create_info, swapchain)))
                {
                    System.out.println("Couldn't create Swapchain");
                    return false;
                }

                this.swapchains.add(new XrSwapchain(swapchain.get(0), this.session));

                //If supported also create Depth Swapchains
                if (this.depth_swapchain_format != -1 && this.depth_supported == true)
                {
                    XrSwapchainCreateInfo depth_swapchain_create_info = XrSwapchainCreateInfo.calloc(stack);
                    depth_swapchain_create_info.type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO);
                    depth_swapchain_create_info.next(NULL);
                    depth_swapchain_create_info.createFlags( 0);
                    depth_swapchain_create_info.usageFlags(XR10.XR_SWAPCHAIN_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
                    depth_swapchain_create_info.format(this.depth_swapchain_format);
                    depth_swapchain_create_info.sampleCount(this.xr_config_views.get(i).recommendedSwapchainSampleCount());
                    depth_swapchain_create_info.width(this.xr_config_views.get(i).recommendedImageRectWidth());
                    depth_swapchain_create_info.height(this.xr_config_views.get(i).recommendedImageRectHeight());
                    depth_swapchain_create_info.faceCount(1);
                    depth_swapchain_create_info.arraySize(1);
                    depth_swapchain_create_info.mipCount(1);

                    PointerBuffer depth_swapchain = stack.callocPointer(1);
                    if (!checkXrResult(XR10.xrCreateSwapchain(this.session, depth_swapchain_create_info, depth_swapchain)))
                    {
                        System.out.println("Couldn't create depth Swapchain");
                        return false;
                    }

                    this.depth_swapchains.add(new XrSwapchain(depth_swapchain.get(0), this.session));
                }
            }
        }

        return true;
    }

    public boolean checkSwapchainImageSupport(int image_format)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer format_count = stack.mallocInt(1);
            if (!checkXrResult(XR10.xrEnumerateSwapchainFormats(this.session, format_count, null)))
            {
                System.out.println("Unable to enumerate Swapchain formats");
                return false;
            }
            LongBuffer formats = stack.callocLong(format_count.get(0));

            if (!checkXrResult(XR10.xrEnumerateSwapchainFormats(this.session, format_count, formats)))
            {
                System.out.println("Unable to enumerate Swapchain formats data");
                return false;
            }
            boolean preferred = false;
            boolean depth_preferred = false;

            int depth_preferred_format = GL40.GL_DEPTH_COMPONENT32F;

            this.swapchain_format = formats.get(0);
            this.depth_swapchain_format = -1;

            while (formats.hasRemaining())
            {
                long format = formats.get();
                if (format == image_format)
                {
                    this.swapchain_format = image_format;
                    preferred = true;
                    System.out.println("Preferred Swapchain Image Format Found");
                }
                if (format == depth_preferred_format)
                {
                    this.depth_swapchain_format = format;
                    depth_preferred = true;
                    System.out.println("Preferred Depth Swapchain Image Format Found");
                }
            }
            if (!preferred)
            {
                return false;
            }

            if (depth_preferred)
            {
                this.depth_supported = true;
            }
        }
        return true;
    }

    public boolean getSwapchainImages()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            for (int i = 0; i < this.swapchains.size(); i++) {
                IntBuffer image_count = stack.callocInt(1);
                if (!checkXrResult(XR10.xrEnumerateSwapchainImages(swapchains.get(i), image_count, null)))
                {
                    System.out.println("Unable to enumerate swapchain images");
                    return false;
                }

                //Allocate space for new XrSwapchainImage in images
                this.images.add(new XrSwapchainImageOpenGLKHR.Buffer(allocateStruct(image_count.get(0), XrSwapchainImageOpenGLKHR.SIZEOF, KHROpenGLEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR)));

                if (!checkXrResult(XR10.xrEnumerateSwapchainImages(swapchains.get(i), image_count, XrSwapchainImageBaseHeader.create(this.images.get(i).address(), this.images.get(i).capacity()))))
                {
                    System.out.println("Unable to enumerate swapchain images data");
                    return false;
                }
            }
            if (this.depth_swapchain_format != -1)
            {
                for (int i = 0; i < this.depth_swapchains.size(); i++) {
                    IntBuffer image_count = stack.callocInt(1);
                    if (!checkXrResult(XR10.xrEnumerateSwapchainImages(depth_swapchains.get(i), image_count, null)))
                    {
                        System.out.println("Unable to enumerate swapchain images");
                        return false;
                    }

                    //Allocate space for new XrSwapchainImage in images
                    this.depth_images.add(new XrSwapchainImageOpenGLKHR.Buffer(allocateStruct(image_count.get(0), XrSwapchainImageOpenGLKHR.SIZEOF, KHROpenGLEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR)));

                    if (!checkXrResult(XR10.xrEnumerateSwapchainImages(depth_swapchains.get(i), image_count, XrSwapchainImageBaseHeader.create(this.depth_images.get(i).address(), this.depth_images.get(i).capacity()))))
                    {
                        System.out.println("Unable to enumerate depth swapchain images data");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean genFrameBuffers()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            int view_count = this.xr_config_views.capacity();
            for (int i = 0; i < view_count; i++)
            {
                //this.frame_buffers.get(i) = new ArrayList<Integer>()
                ArrayList<Integer> buffers = new ArrayList<Integer>();

                IntBuffer frames = stack.mallocInt(this.images.get(i).capacity());
                //this.frame_buffers.add(frames);

                GL40.glGenFramebuffers(frames);
                while (frames.hasRemaining())
                {
                    buffers.add(frames.get());
                }
                this.frame_buffers.add(buffers);
            }
        }
        return true;
    }

    public boolean beginSession()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrSessionBeginInfo session_begin_info = XrSessionBeginInfo.calloc(stack);
            session_begin_info.type(XR10.XR_TYPE_SESSION_BEGIN_INFO);
            session_begin_info.primaryViewConfigurationType(this.view_type);

            if (!checkXrResult(XR10.xrBeginSession(this.session, session_begin_info)))
            {
                System.out.println("Unable to begin sesssion");
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

    // Allocates memory outside of stack
    public ByteBuffer allocateStruct(int capacity, int struct_size, int struct_type)
    {
        //ByteBuffer struct_buffer = stack.malloc(capacity * struct_size);
        ByteBuffer struct_buffer = MemoryUtil.memAlloc(capacity * struct_size);
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

    public boolean XrMainFunction()
    {
        int width2 = this.xr_config_views.get(0).recommendedImageRectWidth();

        this.checkEvents();

        if (this.xr_shutdown == true)
        {
            return false;
        }
        int width = this.xr_config_views.get(0).recommendedImageRectWidth();
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrFrameState frame_state = XrFrameState.calloc(stack);
            frame_state.type(XR10.XR_TYPE_FRAME_STATE);
            frame_state.next(NULL);

            XrFrameWaitInfo frame_wait_info = XrFrameWaitInfo.calloc(stack);
            frame_wait_info.next(NULL);
            frame_wait_info.type(XR10.XR_TYPE_FRAME_WAIT_INFO);

            if (!this.checkXrResult(XR10.xrWaitFrame(this.session, frame_wait_info, frame_state)))
            {
                System.out.println("unable to wait on frame, that probably shouldn't happen");
                return false;
            }

            XrViewLocateInfo view_locate_info = XrViewLocateInfo.calloc(stack);
            view_locate_info.type(XR10.XR_TYPE_VIEW_LOCATE_INFO);
            view_locate_info.next(NULL);
            view_locate_info.viewConfigurationType(this.view_type);
            view_locate_info.displayTime(frame_state.predictedDisplayTime());
            view_locate_info.space(this.reference_space);

            int view_count_raw = this.xr_config_views.capacity();
            XrView.Buffer views = new XrView.Buffer(stack.calloc(view_count_raw * XrView.SIZEOF));// new XrView.Buffer(allocateStruct(view_count, XrView.SIZEOF, XR10.XR_TYPE_VIEW, stack));

            for (int i = 0; i < view_count_raw; i++)
            {
                views.get(i).type(XR10.XR_TYPE_VIEW);
                views.get(i).next(NULL);
            }

            XrViewState view_state = XrViewState.calloc(stack);
            view_state.type(XR10.XR_TYPE_VIEW_STATE);
            view_state.next(NULL);
            IntBuffer view_count = stack.callocInt(1);
            if (!this.checkXrResult(XR10.xrLocateViews(this.session, view_locate_info, view_state, view_count, views)))
            {
                System.out.println("Unable To locate views");
                return false;
            }

            XrFrameBeginInfo frame_begin_info = XrFrameBeginInfo.calloc(stack);
            frame_begin_info.type(XR10.XR_TYPE_FRAME_BEGIN_INFO);
            frame_begin_info.next(NULL);

            if (!this.checkXrResult(XR10.xrBeginFrame(this.session, frame_begin_info)))
            {
                System.out.println("Unable To begin frame");
                return false;
            }

            for (int i = 0; i < view_count_raw; i++)
            {
                XrMatrix4x4f Projection_matrix = new XrMatrix4x4f();
                XrMatrix4x4f.CreateProjectionMatrix(Projection_matrix, XrMatrix4x4f.GraphicsAPI.GRAPHICS_OPENGL, views.get(i).fov(), near_z, far_z);
                XrMatrix4x4f  view_matrix = new XrMatrix4x4f();
                XrMatrix4x4f.CreateViewMatrix(view_matrix, views.get(i).pose().position$(), views.get(i).pose().orientation());

                //Wait to aquire swapchain info
                XrSwapchainImageAcquireInfo swapchain_image_aquire_info = XrSwapchainImageAcquireInfo.calloc(stack);
                swapchain_image_aquire_info.type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO);
                swapchain_image_aquire_info.next(NULL);

                IntBuffer index = stack.callocInt(1);

                if (!checkXrResult(XR10.xrAcquireSwapchainImage(this.swapchains.get(i), swapchain_image_aquire_info, index)))
                {
                    System.out.println("Unable to aquire swapchain image index");
                    return false;
                }

                XrSwapchainImageWaitInfo swapchain_image_wait_info = XrSwapchainImageWaitInfo.calloc(stack);
                swapchain_image_wait_info.type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO);
                swapchain_image_wait_info.next(NULL);
                swapchain_image_wait_info.timeout(1000);

                if (!this.checkXrResult(XR10.xrWaitSwapchainImage(this.swapchains.get(i), swapchain_image_wait_info)))
                {
                    System.out.println("Unable to wait for swapchain image");
                    return false;
                }

                IntBuffer depth_index =  stack.callocInt(1);
                depth_index.put( 0,Integer.MAX_VALUE);

                if (this.swapchain_format != -1)
                {
                    XrSwapchainImageAcquireInfo depth_swapchain_image_aquire_info = XrSwapchainImageAcquireInfo.calloc(stack);
                    depth_swapchain_image_aquire_info.type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO);
                    depth_swapchain_image_aquire_info.next(NULL);

                    if (!this.checkXrResult(XR10.xrAcquireSwapchainImage(this.depth_swapchains.get(i), depth_swapchain_image_aquire_info, depth_index)))
                    {
                        System.out.println("Unable to aquire depth swapchain Image Index");
                        return false;
                    }

                    XrSwapchainImageWaitInfo depth_swapchain_image_wait_info = XrSwapchainImageWaitInfo.calloc(stack);
                    depth_swapchain_image_wait_info.type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO);
                    depth_swapchain_image_wait_info.next(NULL);
                    depth_swapchain_image_wait_info.timeout(1000);
                    if (!this.checkXrResult(XR10.xrWaitSwapchainImage(this.depth_swapchains.get(i), depth_swapchain_image_wait_info)))
                    {
                        System.out.println("Unable to wait for depth swapchain image");
                        return false;
                    }
                }
                this.projection_views.get(i).fov( views.get(i).fov());
                this.projection_views.get(i).pose(views.get(i).pose());

                int depth_image = (this.depth_swapchain_format != -1) ? this.depth_images.get(i).get(depth_index.get(0)).image() : Integer.MAX_VALUE;
                int width3 = this.xr_config_views.get(0).recommendedImageRectWidth();
                boolean result = renderFrame(this.xr_config_views.get(i).recommendedImageRectWidth(), this.xr_config_views.get(i).recommendedImageRectHeight(), Projection_matrix, view_matrix, frame_buffers.get(i).get(index.get(0)), depth_image, this.images.get(i).get(index.get(0)), frame_state.predictedDisplayTime());
                if (!result)
                {
                    System.out.println("Unable to render Frame");
                    return false;
                }

                GL40.glFinish();

                XrSwapchainImageReleaseInfo swapchain_image_release_info = XrSwapchainImageReleaseInfo.calloc(stack);
                swapchain_image_release_info.type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO);
                swapchain_image_release_info.next(NULL);

                if (!this.checkXrResult(XR10.xrReleaseSwapchainImage(this.swapchains.get(i), swapchain_image_release_info)))
                {
                    System.out.println("Unable to release swapchain Image");
                    return false;
                }

                if (this.depth_swapchain_format != -1)
                {
                    XrSwapchainImageReleaseInfo depth_swapchain_image_release_info = XrSwapchainImageReleaseInfo.calloc(stack);
                    depth_swapchain_image_release_info.type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO);
                    depth_swapchain_image_release_info.next(NULL);

                    if (!this.checkXrResult(XR10.xrReleaseSwapchainImage(this.depth_swapchains.get(i), depth_swapchain_image_release_info)))
                    {
                        System.out.println("Unable to release depth swapchain Image");
                        return false;
                    }
                }
            }
            XrCompositionLayerProjection projection_layer = XrCompositionLayerProjection.calloc(stack);
            projection_layer.type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION);
            projection_layer.next(NULL);
            projection_layer.layerFlags(0);
            projection_layer.space(this.reference_space);
            projection_layer.views(this.projection_views);

            XrCompositionLayerBaseHeader compositionLayers = XrCompositionLayerBaseHeader.create(projection_layer);

            PointerBuffer layers = stack.callocPointer(1);

            layers.put(compositionLayers);
            XrFrameEndInfo frame_end_info = XrFrameEndInfo.calloc(stack);
            frame_end_info.type(XR10.XR_TYPE_FRAME_END_INFO);
            frame_end_info.next(NULL);
            frame_end_info.displayTime(frame_state.predictedDisplayTime());
            frame_end_info.environmentBlendMode(XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
            frame_end_info.layerCount(1);
            frame_end_info.layers(layers);

            if (!this.checkXrResult(XR10.xrEndFrame(this.session, frame_end_info)))
            {
                System.out.println("Unable to End Frame");
                return false;
            }
        }

        return true;
    }

    public boolean renderFrame(int width, int height, XrMatrix4x4f perspective_matrix, XrMatrix4x4f view_matrix, int frame_buffer, int depth_buffer, XrSwapchainImageOpenGLKHR image, long predicted_time)
    {
        GL40.glBindFramebuffer(GL40.GL_FRAMEBUFFER, frame_buffer);
        GL40.glClearColor(0, 0, 1, 0);
        GL40.glViewport(0, 0, width, height);
        GL40.glScissor(0, 0, width, height);

        //Clear the Frame Buffer
        GL40.glClear(GL40.GL_COLOR_BUFFER_BIT | GL40.GL_DEPTH_BUFFER_BIT);

        GL40.glFramebufferTexture2D(GL40.GL_FRAMEBUFFER, GL40.GL_COLOR_ATTACHMENT0, GL40.GL_TEXTURE_2D, image.image(), 0);
        //if (depth_buffer != Integer.MAX_VALUE) {
         //   GL40.glFramebufferTexture2D(GL40.GL_FRAMEBUFFER, GL40.GL_DEPTH_ATTACHMENT, GL40.GL_TEXTURE_2D, depth_buffer, 0);
        //}

        XrMatrix4x4f vp_matrix_xr = new XrMatrix4x4f();
        XrMatrix4x4f.Multiply(vp_matrix_xr, perspective_matrix, view_matrix);
        Matrix4f vp_matrix = new Matrix4f(vp_matrix_xr.m[0], vp_matrix_xr.m[1],vp_matrix_xr.m[2],vp_matrix_xr.m[3],
                vp_matrix_xr.m[4], vp_matrix_xr.m[5], vp_matrix_xr.m[6], vp_matrix_xr.m[7], vp_matrix_xr.m[8], vp_matrix_xr.m[9],
                vp_matrix_xr.m[10], vp_matrix_xr.m[11], vp_matrix_xr.m[12], vp_matrix_xr.m[13], vp_matrix_xr.m[14], vp_matrix_xr.m[15]);

        this.square.draw(vp_matrix);
        glfwSwapBuffers(window);
        glfwPollEvents();
        GL40.glBindFramebuffer(GL40.GL_FRAMEBUFFER, 0);

        return true;
    }

    public boolean checkEvents()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            XrEventDataBuffer runtime_event = XrEventDataBuffer.calloc(stack);
            runtime_event.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
            runtime_event.next(NULL);
            int result = XR10.xrPollEvent(this.instance, runtime_event);
            while (result == XR10.XR_SUCCESS)
            {
                switch (runtime_event.type())
                {
                    case(XR10.XR_TYPE_EVENT_DATA_EVENTS_LOST):
                        break;
                    case(XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING):
                        this.xr_shutdown = true;
                        break;
                    case(XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED):
                        break;
                    default:
                        break;
                }

                runtime_event.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
                runtime_event.next(NULL);
                result = XR10.xrPollEvent(this.instance, runtime_event);
            }
        }
        return true;
    }

    public void destroy()
    {
        for (int i = 0; i < xr_config_views.capacity(); i++)
        {
            XR10.xrDestroySwapchain(this.swapchains.get(i));
        }

        for (int i = 0; i < xr_config_views.capacity(); i++)
        {
            XR10.xrDestroySwapchain(this.depth_swapchains.get(i));
        }

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
