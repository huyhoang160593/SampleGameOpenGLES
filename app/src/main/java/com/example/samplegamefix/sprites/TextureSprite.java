package com.example.samplegamefix.sprites;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.example.samplegamefix.GameGLRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đối tượng sprite này sẽ vẽ một hình vuông với texture trong đó
 */
public abstract class TextureSprite implements PoolableSprite
{
    //Khu vực dành riêng cho OpenGL ES
    private static final String MVPMATRIX_PARAM = "uMVPMatrix"; //Ma trận Model-View-Projection
    private static final String POSITION_PARAM = "vPosition";   //Hệ số vị trí
    private static final String TEXTURE_COORDINATE_PARAM = "aTextureCoordinate";    //Hệ tọa độ cho texture

    //Đây là phần code shader để đưa vào graphic pipeline
    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 " + MVPMATRIX_PARAM + ";" +
                    "attribute vec4 " + POSITION_PARAM + ";" +
                    "attribute vec2 " + TEXTURE_COORDINATE_PARAM + ";" +
                    "varying vec2 vTexCoordinate;" +
                    "void main() {" +
                    "  gl_Position = " + MVPMATRIX_PARAM + " * " + POSITION_PARAM + ";" +   //Vị trí trong gl sẽ là phép nhân giữa hệ số vị trí với ma trận MVP
                    "  vTexCoordinate = " + TEXTURE_COORDINATE_PARAM + ";" +    //gán hệ tọa độ cho texture
                    "}";

    //Đây là phần code fragment để đưa vào graphic pipeline
    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
                    "uniform sampler2D uTexture;" +
                    "varying vec2 vTexCoordinate;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D(uTexture, vTexCoordinate); " +  //gán màu vào texture 2D sử dụng hệ tọa độ texture đã cho ở phần shader
                    "}";

    /** Gán tọa độ cho texture
     *  tọa độ cho texture là một hình vuông 2d với mỗi đỉnh tương ứng với các tọa độ x,y
     **/

    private static final float TEXTURE_COORDINATES[] =
            {
                    0.0f, 1.0f, //x,y
                    1.0f, 1.0f, //x,y
                    1.0f, 0.0f, //x,y
                    0.0f, 0.0f  //x,y
            };

    /**
     *  số lượng hệ tọa độ quy chiếu cho mỗi đỉnh trong mảng hiện tại
     *  số lượng đó sẽ là ba vì ta đang vẽ trong không gian ba chiều
     */
    private static final int COORDS_PER_VERTEX = 3;

    /** gán tọa độ cho hình vuông chứa sprite
     * Nếu bạn để ý thì vì đây là tọa độ 2 chiều nên chiều z luôn luôn là 0
     * */
    private static float SQUARE_COORDINATES[] =
            {
                    -1.0f, -1.0f, 0.0f, //x,y,z, đỉnh 0, dưới trái
                     1.0f, -1.0f, 0.0f, //x,y,z, đỉnh 1, trên trái
                     1.0f,  1.0f, 0.0f, //x,y,z, đỉnh 2, trên phải
                    -1.0f,  1.0f, 0.0f //x,y,z, đỉnh 3, dưới phải
            };

    /** Thứ tự để vẽ hình vuông chứa sprite
     * Đây là thứ tự thường thấy để vẽ hình vuông trong OpenGL
     * Bạn đang thắc mắc 0,1,2,3 là cái gì??? Đó chính là thứ tự đỉnh ta đã được đa đưa vào ở ngay trên, thứ tự vẽ đỉnh trong OpenGL sẽ diễn ra đúng như thế
     * */
    private static final short DRAW_ORDER[] = { 0, 1, 2, 0, 2, 3 };

    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes cho mỗi vertex = 32 bit lưu giá trị

    /**
     * Khởi tạo các bộ đệm lưu giữ lần lượt tọa độ điểm, thứ tự vẽ hình và lưu giữ texture
     * @_vertexBuffer là bộ đệm lưu giữ tọa tọa độ các đỉnh của hình khối muốn vẽ, với các tọa độ ở dạng float
     * @_drawListBuffer là bộ đệm lưu giữa thứ tự vẽ các đỉnh, vì là thứ tự nên sẽ dùng số nguyên dương, ta dùng short
     * @_textureBuffer là bộ đệm dùng để trợ giúp việc vẽ texture lên hình khối, tương tự như _vertexBuffer nên dùng float
     *  **/
    private static FloatBuffer _vertexBuffer;
    private static ShortBuffer _drawListBuffer;
    private static FloatBuffer _textureBuffer;

    protected static int _programHandle;

    protected static int positionHandle = -1;
    protected static int mvpMatrixHandle = -1;
    protected static int textureCoordinateHandle = -1;

    protected int _textureDataHandle;
    protected int _drawableResourceId;

    private static Map<Integer, Integer> drawableToTextureMap = new HashMap<>();
    private static Map<Integer, Integer> bitmapToTextureMap = new HashMap<>();

    //Kết thúc khu vực


    // Khu vực lưu trữ trạng thái sprite
    protected float _currentPos[] = new float[3];
    protected float _vector[] = new float[3];

    /**
     * Trung tâm của sprite cần vẽ có thể được tùy chỉnh hợp lý cho việc xác định các va chạm(collision)
     * Giá trị nên được giữ ở mức giữa -1 và 1
     */
    protected float _center[] = new float[2];
    /**
     * bán kính(radius) của sprite có thể được tùy chỉnh cho việc xác định các va chạm
     * giá trị nên được giữ ở mức giữa 0 và 1
     */
    protected float _radius = 1;

    /**
     * tỉ lệ(scale)của sprite hiện tại sẽ là một mảng chứa giá trị thể hiện tỉ lệ bình thường của x:y là 1:1 v
     */
    protected float _currentScale[] = new float[] { 1.0f, 1.0f };
    //Biến dùng cho việc gán sprite có thể tự xoay quanh trục z nếu muốn
    protected float _rotationZ = 0.0f;

    protected boolean _alive = true;
    protected float _imageRatio;

    protected boolean _inUse;

    //Khởi tạo ma trận gốc dùng cho việc nhân ma trận để ánh xạ vào OpenGL về sau
    private float scratchMatrix[] = new float[16];
    // Kết thúc khu vực

    protected Context _context;

    //Phần Constructor dùng để khởi tạo đối tượng mới, vì chúng ta không sử dụng nên việc khởi tạo chỉ để trình biên dịch java không báo lỗi
    protected TextureSprite ()
    {
    }

    public void setDrawable(int drawableResourceId)
    {
        _drawableResourceId = drawableResourceId;
        _textureDataHandle = loadGLTexture(drawableResourceId);
    }

    public void setContext (Context context)
    {
        _context = context;
    }

    /** the buffers never need to change from sprite to sprite so share them statically
     * Phần đọc bộ đệm để gán vào OpenGL ES không thay đổi giữa việc vẽ các sprite nên chúng ta có thể viết một lần dùng cho tất cả */
    public static void initGlState ()
    {
        // Khởi tạo bộ đệm đỉnh cho các tọa độ của hình khối
        ByteBuffer bb = ByteBuffer.allocateDirect(SQUARE_COORDINATES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        _vertexBuffer = bb.asFloatBuffer();
        _vertexBuffer.put(SQUARE_COORDINATES);
        _vertexBuffer.position(0);

        // Khởi tạo bộ đệm texture cho các tọa độ của texture
        bb = ByteBuffer.allocateDirect(TEXTURE_COORDINATES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        _textureBuffer = bb.asFloatBuffer();
        _textureBuffer.put(TEXTURE_COORDINATES);
        _textureBuffer.position(0);

        // Bộ đệm đỉnh này thì chứa thứ tự vẽ các tam giác
        ByteBuffer dlb = ByteBuffer.allocateDirect(DRAW_ORDER.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        _drawListBuffer = dlb.asShortBuffer();
        _drawListBuffer.put(DRAW_ORDER);
        _drawListBuffer.position(0);

        int vertexShader = GameGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = GameGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);
        _programHandle = GLES20.glCreateProgram();             // Khởi tạo một chương trình rỗng
        GLES20.glAttachShader(_programHandle, vertexShader);   // Nạp vào vertexShader(phủ lên đỉnh)
        GLES20.glAttachShader(_programHandle, fragmentShader); // Nạp vào fragmentShader(phủ lên mảnh)

        GLES20.glBindAttribLocation(_programHandle, 0, TEXTURE_COORDINATE_PARAM); //Giá hệ tọa độ texture lên hình đã vẽ
        GLES20.glLinkProgram(_programHandle); // gán tất cả chương trình vào kết nối chuẩn bị cho việc chạy về sau

        /** Phần này sẽ định nghĩa vị trí trong bộ nhớ cho các biến trong phần code GLSL một cách tự động,
         * ta có thể tự định nghĩa vị trí(location) cho nó bằng glBinhAttribLocation hoặc viết thẳng vào
         * phần vertexShader ở trên nếu muốn (nhưng không khuyến khích - nhất là gà mờ như tôi và bạn)
        */
        positionHandle = GLES20.glGetAttribLocation(_programHandle, POSITION_PARAM);
        textureCoordinateHandle = GLES20.glGetAttribLocation(_programHandle, TEXTURE_COORDINATE_PARAM);
        mvpMatrixHandle = GLES20.glGetUniformLocation(_programHandle, MVPMATRIX_PARAM);
    }

    /**
     * Cập nhật trạng thái của các sprite mỗi vòng vẽ mới
     *
     * @trảvề true nếu sprite vẫn muốn sống, false nếu như nó đã xong nhiệm vụ của mình và sẵn sàng để chết
     */
    public abstract boolean update ();

    /**
     * Chúng ta sẽ không vẽ một hình một đúng chứ, nên đây là phương thức giúp chúng ta vẽ các hình
     * theo tá(số lượng lớn)
     * */
    public void batchDraw (float[] mvpMatrix, List<TextureSprite> sprites)
    {
        // Thêm chương trình vào môi trường của OpenGL
        GLES20.glUseProgram(_programHandle);

        // Enable a handle to the vertices
        //We assigned the attribute index of the position attribute to 0 in the vertex shader, so the call to glEnableVertexAttribArray(0)
        // enables the attribute index for the position attribute.
        // ... If the attribute is not enabled, it will not be used during rendering.

        /**
         * Kích hoạt chỉ số cho mảng các đỉnh, bắt buộc phải dùng nếu không mảng đỉnh ta đã định
         * nghĩa ở trên sẽ không được sử dụng, thêm vào đó sẽ không thể sử dụng được glVertexAttribPointer
         * ở ngay dưới dây
         * positionHandle chính là vị trí được định nghĩa trong bộ nhớ mà ta vừa thiết lập ở phần initGlState
         *  */
        GLES20.glEnableVertexAttribArray(positionHandle);

        /** phần chuẩn bị dữ liệu tọa độ
         * Đây mới chính là phần quan trọng nè
         * Dữ liệu về các đỉnh của ta sẽ được định dạng như sau:
          - Dữ liệu vị trí được lưu giữ trong số thập phân 32 bit bằng cách dùng float
          - Mỗi một vị trí sẽ được tạo ra bởi 4 giá trị trong 32 bit đó
          - Sẽ không có bất kì khoảng cách nào giữa các bộ giá trị đó. Chúng được đóng gói vừa khít trong một mảng
          - Giá trị đầu tiên trong mảng sẽ là phần bắt đầu của bộ đệm
         glVertexAttribPointer sẽ nói cho OpenGL ES tất cả đống kia
         * Tham số thứ 3 - @GLES20.GL_FLOAT sẽ đại diện cho bộ nhớ của số float, tương ứng với 32 bit
         * Tham số thứ 2 - COORDS_PER_VERTEX định nghĩa bao nhiêu giá trị trong đó đại diện cho một mảnh của dữ liệu
         * Tham số thứ 5 xác định khoảng cách giữa các bộ dữ liệu, nó là 0 tức là không có khoảng cách nào, ở đây là 12
         * Tham số thứ 6 xác định số byte offset khỏi giá trị của bộ đệm ở phía trước
         * Tham số thứ 4 là chuẩn hóa, ta không cần quan tâm vì đơn giản nó đa số các trường hợp là dùng false
         */
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, _vertexBuffer);

        // Tương tự cho phần texture, nó sẽ được vẽ đè lên hình khối đã được xây dựng lên trước của ta, nên bộ đệm texture mới để vị trí 0
        _textureBuffer.position(0);
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, _textureBuffer);

        int size = sprites.size();
        for (int i = 0; i < size; i++)
        {
            System.arraycopy(mvpMatrix, 0, scratchMatrix, 0, 16);

            TextureSprite sprite = sprites.get(i);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sprite._textureDataHandle);

            // di chuyển sprite đến bị trí hiện tại của nó
            Matrix.translateM(scratchMatrix, 0, sprite._currentPos[0], sprite._currentPos[1], sprite._currentPos[2]);
            // xoay sprite
            Matrix.rotateM(scratchMatrix, 0, sprite._rotationZ, 0, 0, 1.0f);
            // xác định tỉ lệ của sprite đó với sprite gốc
            Matrix.scaleM(scratchMatrix, 0, sprite._currentScale[0], sprite._currentScale[1], 1);

            // Apply the projection and view transformation - Sử dụng phép chuyển hình chiếu và phép chuyển góc nhìn cho sprite
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, scratchMatrix, 0);

            // Cuối cùng sau tất tả các bước, hình ảnh của chúng ta sẽ được xuất ra ở đây
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, DRAW_ORDER.length, GLES20.GL_UNSIGNED_SHORT, _drawListBuffer);
        }

        // Disable vertex array - Sau khi vẽ xong, ta hủy kích hoạt mảng các đỉnh để giải phóng bộ nhớ
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
    }

    //Đây là phương thức cho phép ta vẽ với một hình
    public void draw (float[] mvpMatrix)
    {
        System.arraycopy(mvpMatrix, 0, scratchMatrix, 0, 16);

        // Thêm chương trình vào môi trường của OpenGL
        GLES20.glUseProgram(_programHandle);

        // Kích hoạt chỉ số cho mảng các đỉnh
        GLES20.glEnableVertexAttribArray(positionHandle);

        // phần chuẩn bị dữ liệu tọa độ
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, _vertexBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _textureDataHandle);

        _textureBuffer.position(0);
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, _textureBuffer);

        // di chuyển sprite đến bị trí hiện tại của nó
        Matrix.translateM(scratchMatrix, 0, _currentPos[0], _currentPos[1], _currentPos[2]);
        // xoay sprite
        Matrix.rotateM(scratchMatrix, 0, _rotationZ, 0, 0, 1.0f);
        // xác định tỉ lệ của sprite đó với sprite gốc
        Matrix.scaleM(scratchMatrix, 0, _currentScale[0], _currentScale[1], 1);

        // Apply the projection and view transformation - Sử dụng phép chuyển hình chiếu và phép chuyển góc nhìn cho sprite
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, scratchMatrix, 0);

        // Đồ họa sẽ được vẽ ra ở bước này
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, DRAW_ORDER.length, GLES20.GL_UNSIGNED_SHORT, _drawListBuffer);

        // Sau khi vẽ xong, ta hủy kích hoạt mảng các đỉnh để giải phóng bộ nhớ
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
    }

    /**
     * Phương thức xác định va chạm một cách đơn giản bằng việc giả định mỗi sprite sẽ là một hình tròn
     * Thực hiện việc tính toán để xem xem khoảng cách giữa 2 sprite có nhỏ hơn tổng của bán kính 2 sprite đó
     *
     * By default a sprite has a collision circle centered in the sprite with radius 1 but you can
     * nhưng bạn có thể tùy chỉnh nó nếu như bạn muốn một vòng tròn nhỏ hơn hoặc muốn bù(offset) nó
      !Note: Adjusts the center and radius using the scale of the sprite IN THE X DIRECTION
      !Note: Nên tùy chỉnh tâm và bán kính sử dụng tỉ lệ của sprite THEO HƯỚNG X
     */
    public boolean collidesWith (TextureSprite other)
    {
        float dist = distance(
                _currentPos[0] + _center[0] * _currentScale[0],
                _currentPos[1] + _center[1] * _currentScale[0],
                other._currentPos[0] + other._center[0] * other._currentScale[0],
                other._currentPos[1] + other._center[1] * other._currentScale[1]);
        if (dist < _radius * _currentScale[0] + other._radius * other._currentScale[0])
        {
            return true;
        }
        return false;
    }

    float distance (float x1, float y1, float x2, float y2)
    {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    public static void clearTextureCache ()
    {
        drawableToTextureMap.clear();
        bitmapToTextureMap.clear();
    }

    /**
     * Ta sẽ nạp texture từ drawable của android(đương nhiên vì đây là game android mà :( )
     */
    protected int loadGLTexture (int drawableResourceId)
    {
        Integer cachedTextureId = drawableToTextureMap.get(drawableResourceId);
        if (cachedTextureId != null)
        {
            return cachedTextureId;
        }

        // Nạp texture
        Bitmap bitmap = BitmapFactory.decodeResource(_context.getResources(), drawableResourceId);
        int handle = loadGLTexture(bitmap);

        drawableToTextureMap.put(drawableResourceId, handle);

        // Giải phóng bộ nhớ
        bitmap.recycle();

        return handle;
    }

    /**
     * Phương thức nạp texture với lựa chọn sử dụng bitmap
     */
    protected int loadGLTexture (Bitmap bitmap)
    {
        Integer cachedTextureId = bitmapToTextureMap.get(bitmap.hashCode());
        if (cachedTextureId != null)
        {
            return cachedTextureId;
        }

        _imageRatio = bitmap.getWidth() / ((float) bitmap.getHeight());

        // Khởi tạo một con trỏ texture và nhúng nó vô phần xử lý của ta
        int[] textureHandle = new int[1];
        GLES20.glGenTextures(1, textureHandle, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

        // tạo ra bộ lọc texture gần(nearest filtered)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // Sử dụng Android GLUtils để định nghĩa hình ảnh texture 2 chiều từ bitmap của ta
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0);

        bitmapToTextureMap.put(bitmap.hashCode(), textureHandle[0]);

        return textureHandle[0];
    }

    public void reloadTexture ()
    {
        if (_drawableResourceId != 0)
        {
            _textureDataHandle = loadGLTexture(_drawableResourceId);
        }
    }

    public boolean isInUse ()
    {
        return _inUse;
    }

    public void setInUse (boolean inUse)
    {
        _inUse = inUse;
    }

    public boolean isAlive ()
    {
        return _alive;
    }

    public void setAlive (boolean alive)
    {
        _alive = alive;
    }

    public float[] getPosition ()
    {
        return _currentPos;
    }

    public float[] getVector ()
    {
        return _vector;
    }

    public float[] getScale ()
    {
        return _currentScale;
    }
}

