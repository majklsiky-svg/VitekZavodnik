package cz.vitekzavodnik.game;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public final class Mesh {
    private final FloatBuffer vertices;
    private final int vertexCount;

    public Mesh(float[] interleavedPositionNormal) {
        vertices = ByteBuffer.allocateDirect(interleavedPositionNormal.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertices.put(interleavedPositionNormal).position(0);
        vertexCount = interleavedPositionNormal.length / 6;
    }

    public void draw(int aPosition, int aNormal) {
        vertices.position(0);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 24, vertices);
        GLES20.glEnableVertexAttribArray(aPosition);

        vertices.position(3);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, vertices);
        GLES20.glEnableVertexAttribArray(aNormal);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aNormal);
    }
}
