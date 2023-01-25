package LWJGLOpenXRSample.openxr;

import org.lwjgl.openxr.XrFovf;
import org.lwjgl.openxr.XrQuaternionf;
import org.lwjgl.openxr.XrVector3f;

public class XrMatrix4x4f {
    float[] m = new float[16];

    enum GraphicsAPI { GRAPHICS_VULKAN, GRAPHICS_OPENGL, GRAPHICS_OPENGL_ES, GRAPHICS_D3D};

    public XrMatrix4x4f()
    {

    }

    public static void CreateProjectionMatrix(XrMatrix4x4f result, GraphicsAPI graphicsapi, XrFovf fov, float nearZ, float farZ)
    {
        float tanLeft  = (float)Math.tan(fov.angleLeft());
        float tanRight = (float)Math.tan(fov.angleRight());

        float tanDown  = (float)Math.tan(fov.angleDown());
        float tanUp    = (float)Math.tan(fov.angleUp());

        CreateProjectionMatrix(result, graphicsapi, tanLeft, tanRight, tanUp, tanDown, nearZ, farZ);
    }

    public static void CreateProjectionMatrix(XrMatrix4x4f result, GraphicsAPI graphicsapi, float tanAngleLeft,float tanAngleRight,float tanAngleUp,float tanAngleDown, float nearZ, float farZ)
    {
        // Creates a projection matrix based on the specified dimensions.
        // The projection matrix transforms -Z=forward, +Y=up, +X=right to the appropriate clip space for the graphics API.
        // The far plane is placed at infinity if farZ <= nearZ.
        // An infinite projection matrix is preferred for rasterization because, except for
        // things *right* up against the near plane, it always provides better precision:
        //              "Tightening the Precision of Perspective Rendering"
        //              Paul Upchurch, Mathieu Desbrun
        //              Journal of Graphics Tools, Volume 16, Issue 1, 2012
        float tanAngleWidth = tanAngleRight - tanAngleLeft;

        // Set to tanAngleDown - tanAngleUp for a clip space with positive Y down (Vulkan).
        // Set to tanAngleUp - tanAngleDown for a clip space with positive Y up (OpenGL / D3D / Metal).
        float tanAngleHeight = (graphicsapi == GraphicsAPI.GRAPHICS_VULKAN) ? (tanAngleDown - tanAngleUp) : (tanAngleUp - tanAngleDown);

        // Set to nearZ for a [-1,1] Z clip space (OpenGL / OpenGL ES).
        // Set to zero for a [0,1] Z clip space (Vulkan / D3D / Metal).
        float offsetZ = (graphicsapi == GraphicsAPI.GRAPHICS_OPENGL || graphicsapi == GraphicsAPI.GRAPHICS_OPENGL_ES) ? nearZ : 0;

        if (farZ < nearZ)
        {
            // place the far plane at infinity
            result.m[0] = 2.0f / tanAngleWidth;
            result.m[4] = 0.0f;
            result.m[8] = (tanAngleRight + tanAngleLeft) / tanAngleWidth;
            result.m[12] = 0.0f;

            result.m[1] = 0.0f;
            result.m[5] = 2.0f / tanAngleHeight;
            result.m[9] = (tanAngleUp + tanAngleDown) / tanAngleHeight;
            result.m[13] = 0.0f;

            result.m[2] = 0.0f;
            result.m[6] = 0.0f;
            result.m[10] = -1.0f;
            result.m[14] = -(nearZ + offsetZ);

            result.m[3] = 0.0f;
            result.m[7] = 0.0f;
            result.m[11] = -1.0f;
            result.m[15] = 0.0f;
        }
        else
        {
            // normal projection
            result.m[0] = 2.0f / tanAngleWidth;
            result.m[4] = 0.0f;
            result.m[8] = (tanAngleRight + tanAngleLeft) / tanAngleWidth;
            result.m[12] = 0.0f;

            result.m[1] = 0.0f;
            result.m[5] = 2.0f / tanAngleHeight;
            result.m[9] = (tanAngleUp + tanAngleDown) / tanAngleHeight;
            result.m[13] = 0.0f;

            result.m[2] = 0.0f;
            result.m[6] = 0.0f;
            result.m[10] = -(farZ + offsetZ) / (farZ - nearZ);
            result.m[14] = -(farZ * (nearZ + offsetZ)) / (farZ - nearZ);

            result.m[3] = 0.0f;
            result.m[7] = 0.0f;
            result.m[11] = -1.0f;
            result.m[15] = 0.0f;
        }
    }

    public static void CreateViewMatrix(XrMatrix4x4f result, XrVector3f translation, XrQuaternionf rotation)
    {
        XrMatrix4x4f rotationMatrix = new XrMatrix4x4f();
        CreateFromQuaternion(rotationMatrix, rotation);

        XrMatrix4x4f translationMatrix = new XrMatrix4x4f();
        CreateTranslation(translationMatrix, translation.x(), translation.y(), translation.z());

        XrMatrix4x4f viewMatrix = new XrMatrix4x4f();
        Multiply(viewMatrix, translationMatrix, rotationMatrix);

        Invert(result, viewMatrix);
    }

    public static void CreateFromQuaternion(XrMatrix4x4f result, XrQuaternionf quat)
    {
         float x2 = quat.x() + quat.x();
         float y2 = quat.y() + quat.y();
         float z2 = quat.z() + quat.z();

         float xx2 = quat.x() * x2;
         float yy2 = quat.y() * y2;
         float zz2 = quat.z() * z2;

         float yz2 = quat.y() * z2;
         float wx2 = quat.w() * x2;
         float xy2 = quat.x() * y2;
         float wz2 = quat.w() * z2;
         float xz2 = quat.x() * z2;
         float wy2 = quat.w() * y2;

        result.m[0] = 1.0f - yy2 - zz2;
        result.m[1] = xy2 + wz2;
        result.m[2] = xz2 - wy2;
        result.m[3] = 0.0f;

        result.m[4] = xy2 - wz2;
        result.m[5] = 1.0f - xx2 - zz2;
        result.m[6] = yz2 + wx2;
        result.m[7] = 0.0f;

        result.m[8] = xz2 + wy2;
        result.m[9] = yz2 - wx2;
        result.m[10] = 1.0f - xx2 - yy2;
        result.m[11] = 0.0f;

        result.m[12] = 0.0f;
        result.m[13] = 0.0f;
        result.m[14] = 0.0f;
        result.m[15] = 1.0f;
    }

    public static void CreateTranslation(XrMatrix4x4f result, float x, float y, float z)
    {
        result.m[0] = 1.0f;
        result.m[1] = 0.0f;
        result.m[2] = 0.0f;
        result.m[3] = 0.0f;
        result.m[4] = 0.0f;
        result.m[5] = 1.0f;
        result.m[6] = 0.0f;
        result.m[7] = 0.0f;
        result.m[8] = 0.0f;
        result.m[9] = 0.0f;
        result.m[10] = 1.0f;
        result.m[11] = 0.0f;
        result.m[12] = x;
        result.m[13] = y;
        result.m[14] = z;
        result.m[15] = 1.0f;
    }

    public static void Multiply(XrMatrix4x4f result, XrMatrix4x4f a, XrMatrix4x4f b)
    {
        result.m[0] = a.m[0] * b.m[0] + a.m[4] * b.m[1] + a.m[8] * b.m[2] + a.m[12] * b.m[3];
        result.m[1] = a.m[1] * b.m[0] + a.m[5] * b.m[1] + a.m[9] * b.m[2] + a.m[13] * b.m[3];
        result.m[2] = a.m[2] * b.m[0] + a.m[6] * b.m[1] + a.m[10] * b.m[2] + a.m[14] * b.m[3];
        result.m[3] = a.m[3] * b.m[0] + a.m[7] * b.m[1] + a.m[11] * b.m[2] + a.m[15] * b.m[3];

        result.m[4] = a.m[0] * b.m[4] + a.m[4] * b.m[5] + a.m[8] * b.m[6] + a.m[12] * b.m[7];
        result.m[5] = a.m[1] * b.m[4] + a.m[5] * b.m[5] + a.m[9] * b.m[6] + a.m[13] * b.m[7];
        result.m[6] = a.m[2] * b.m[4] + a.m[6] * b.m[5] + a.m[10] * b.m[6] + a.m[14] * b.m[7];
        result.m[7] = a.m[3] * b.m[4] + a.m[7] * b.m[5] + a.m[11] * b.m[6] + a.m[15] * b.m[7];

        result.m[8] = a.m[0] * b.m[8] + a.m[4] * b.m[9] + a.m[8] * b.m[10] + a.m[12] * b.m[11];
        result.m[9] = a.m[1] * b.m[8] + a.m[5] * b.m[9] + a.m[9] * b.m[10] + a.m[13] * b.m[11];
        result.m[10] = a.m[2] * b.m[8] + a.m[6] * b.m[9] + a.m[10] * b.m[10] + a.m[14] * b.m[11];
        result.m[11] = a.m[3] * b.m[8] + a.m[7] * b.m[9] + a.m[11] * b.m[10] + a.m[15] * b.m[11];

        result.m[12] = a.m[0] * b.m[12] + a.m[4] * b.m[13] + a.m[8] * b.m[14] + a.m[12] * b.m[15];
        result.m[13] = a.m[1] * b.m[12] + a.m[5] * b.m[13] + a.m[9] * b.m[14] + a.m[13] * b.m[15];
        result.m[14] = a.m[2] * b.m[12] + a.m[6] * b.m[13] + a.m[10] * b.m[14] + a.m[14] * b.m[15];
        result.m[15] = a.m[3] * b.m[12] + a.m[7] * b.m[13] + a.m[11] * b.m[14] + a.m[15] * b.m[15];
    }

    public static void Invert(XrMatrix4x4f result, XrMatrix4x4f src)
    {
         float rcpDet =
            1.0f / (src.m[0] * XrMatrix4x4f_Minor(src, 1, 2, 3, 1, 2, 3) - src.m[1] * XrMatrix4x4f_Minor(src, 1, 2, 3, 0, 2, 3) +
                    src.m[2] * XrMatrix4x4f_Minor(src, 1, 2, 3, 0, 1, 3) - src.m[3] * XrMatrix4x4f_Minor(src, 1, 2, 3, 0, 1, 2));

        result.m[0] = XrMatrix4x4f_Minor(src, 1, 2, 3, 1, 2, 3) * rcpDet;
        result.m[1] = -XrMatrix4x4f_Minor(src, 0, 2, 3, 1, 2, 3) * rcpDet;
        result.m[2] = XrMatrix4x4f_Minor(src, 0, 1, 3, 1, 2, 3) * rcpDet;
        result.m[3] = -XrMatrix4x4f_Minor(src, 0, 1, 2, 1, 2, 3) * rcpDet;
        result.m[4] = -XrMatrix4x4f_Minor(src, 1, 2, 3, 0, 2, 3) * rcpDet;
        result.m[5] = XrMatrix4x4f_Minor(src, 0, 2, 3, 0, 2, 3) * rcpDet;
        result.m[6] = -XrMatrix4x4f_Minor(src, 0, 1, 3, 0, 2, 3) * rcpDet;
        result.m[7] = XrMatrix4x4f_Minor(src, 0, 1, 2, 0, 2, 3) * rcpDet;
        result.m[8] = XrMatrix4x4f_Minor(src, 1, 2, 3, 0, 1, 3) * rcpDet;
        result.m[9] = -XrMatrix4x4f_Minor(src, 0, 2, 3, 0, 1, 3) * rcpDet;
        result.m[10] = XrMatrix4x4f_Minor(src, 0, 1, 3, 0, 1, 3) * rcpDet;
        result.m[11] = -XrMatrix4x4f_Minor(src, 0, 1, 2, 0, 1, 3) * rcpDet;
        result.m[12] = -XrMatrix4x4f_Minor(src, 1, 2, 3, 0, 1, 2) * rcpDet;
        result.m[13] = XrMatrix4x4f_Minor(src, 0, 2, 3, 0, 1, 2) * rcpDet;
        result.m[14] = -XrMatrix4x4f_Minor(src, 0, 1, 3, 0, 1, 2) * rcpDet;
        result.m[15] = XrMatrix4x4f_Minor(src, 0, 1, 2, 0, 1, 2) * rcpDet;
    }

    public static float XrMatrix4x4f_Minor(XrMatrix4x4f matrix, int r0, int r1, int r2, int c0, int c1, int c2)
    {
        return matrix.m[4 * r0 + c0] *
                (matrix.m[4 * r1 + c1] * matrix.m[4 * r2 + c2] - matrix.m[4 * r2 + c1] * matrix.m[4 * r1 + c2]) -
                matrix.m[4 * r0 + c1] *
                        (matrix.m[4 * r1 + c0] * matrix.m[4 * r2 + c2] - matrix.m[4 * r2 + c0] * matrix.m[4 * r1 + c2]) +
                matrix.m[4 * r0 + c2] *
                        (matrix.m[4 * r1 + c0] * matrix.m[4 * r2 + c1] - matrix.m[4 * r2 + c0] * matrix.m[4 * r1 + c1]);
    }

}
