package de.herb64.funinspace;

import android.content.Context;
import android.content.Intent;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/*
 * This activity is just started to query the maximum texture size of the device, so that
 * The hires bitmap display can be scaled appropriately to avoid black image displays
 */
public class TexSizeActivity extends AppCompatActivity {

    private Intent returnIntent;
    private TexSizeActivity act = this;     // we use this to finish ourselves automatically

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        returnIntent = new Intent();
        TexSizeActivity.MyGLSurfaceView surface = new TexSizeActivity.MyGLSurfaceView(this);
        setContentView(surface);
    }


    // OpenGL STUFF goes here :)
    private class MyGLSurfaceView extends GLSurfaceView {

        MyGLSurfaceRenderer renderer;

        public MyGLSurfaceView(Context context) {
            super(context);
            setEGLContextClientVersion(2);
            renderer = new MyGLSurfaceRenderer(this);
            setRenderer(renderer);
        }
    }

    // TODO how to noinspect the unused parameter?
    //noinspection UnusedParameters
    private class MyGLSurfaceRenderer implements GLSurfaceView.Renderer {
        //noinspection UnusedParameters
        private MyGLSurfaceRenderer(MyGLSurfaceView surface) {
            //noinspection UnusedParameters
        }

        @Override
        public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
            GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
            int[] max = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0);
            returnIntent.putExtra("maxTextureSize", max[0]);
            setResult(RESULT_OK, returnIntent);
            act.finish();
        }

        @Override
        public void onSurfaceChanged(GL10 gl10, int i, int i1) {
        }

        @Override
        public void onDrawFrame(GL10 gl10) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
    }

}
