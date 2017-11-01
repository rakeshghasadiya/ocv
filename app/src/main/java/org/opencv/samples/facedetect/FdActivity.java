package org.opencv.samples.facedetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class FdActivity extends Activity implements CvCameraViewListener2, SeekBar.OnSeekBarChangeListener {

    private static final String TAG = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    public static final int NATIVE_DETECTOR = 1;

    private MenuItem mItemFace50;
    private MenuItem mItemFace40;
    private MenuItem mItemFace30;
    private MenuItem mItemFace20;
    private MenuItem mItemType;

    private Mat mRgba;
    private Mat mGray;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private DetectionBasedTracker mNativeDetector;

    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 1;


    private int param1 = 50;
    private int param2 = 45;

    private SeekBar param1Seekbar;
    private SeekBar param2Seekbar;

    private TextView tvParam1;
    private TextView tvParam2;

    private Button flipcameraButton;

    private int cameraIndex = 0;

    private Mat mIntermediateMat;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.haarcascade_upperbody);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "haarcascade_upperbody.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public FdActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        param1Seekbar = (SeekBar) findViewById(R.id.param1seekbar);
        param1Seekbar.setOnSeekBarChangeListener(this);
        param2Seekbar = (SeekBar) findViewById(R.id.param2seekbar);
        param2Seekbar.setOnSeekBarChangeListener(this);

        tvParam1 = (TextView) findViewById(R.id.tvparam1);
        tvParam2 = (TextView) findViewById(R.id.tvparam2);

        flipcameraButton = (Button) findViewById(R.id.flipcamera);
        flipcameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraIndex = cameraIndex == 0 ? 1 : 0;
                mOpenCvCameraView.setCameraIndex(cameraIndex);
                if (mOpenCvCameraView.isEnabled()) {
                    mOpenCvCameraView.disableView();
                }
                mOpenCvCameraView.enableView();
            }
        });

        tvParam1.setText(param1 + "");
        tvParam2.setText(param2 + "");

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(cameraIndex);
        mOpenCvCameraView.setMaxFrameSize(320, 240);


    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();

    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();

        mIntermediateMat = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();


        if (mIntermediateMat != null)
            mIntermediateMat.release();

        mIntermediateMat = null;

    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();


        Size sizeRgba = rgba.size();

        Mat rgbaInnerWindow;
        int rows = (int) sizeRgba.height;
        int cols = (int) sizeRgba.width;

        int left = 0;
        int top = 0;

        int width = cols;
        int height = rows;


        rgbaInnerWindow = rgba
                .submat(top, top + height, left, left + width);
        Imgproc.cvtColor(rgbaInnerWindow, rgbaInnerWindow,
                Imgproc.COLOR_RGB2GRAY);
        Mat circles = rgbaInnerWindow.clone();
        rgbaInnerWindow = rgba
                .submat(top, top + height, left, left + width);
        Imgproc.GaussianBlur(rgbaInnerWindow, rgbaInnerWindow, new Size(5,
                5), 2, 2);
        Imgproc.Canny(rgbaInnerWindow, mIntermediateMat, 13, 43);
        Imgproc.HoughCircles(mIntermediateMat, circles,
                Imgproc.CV_HOUGH_GRADIENT, 1, 300, param1, param2, 30, 100);// 1, 300, 50, 45, 30, 100);
        Imgproc.cvtColor(mIntermediateMat, rgbaInnerWindow, Imgproc.COLOR_GRAY2BGRA, 4);

        for (int x = 0; x < circles.cols(); x++) {
            double vCircle[] = circles.get(0, x);
            if (vCircle == null)
                break;
            Point pt = new Point(Math.round(vCircle[0]),
                    Math.round(vCircle[1]));
            int radius = (int) Math.round(vCircle[2]);
            Log.d("cv", pt + " radius " + radius);
            Imgproc.circle(rgbaInnerWindow, pt, 3, new Scalar(0, 0, 255), 5);
            Imgproc.circle(rgbaInnerWindow, pt, radius, new Scalar(255, 0, 0), 5);

        }
        rgbaInnerWindow.release();


            Point center = new Point(rgba.width()/2,rgba.height()/2);
            double angle = 90;
            double angle1 = 270;

            double scale = 1.0;
            Mat dst = new Mat();
            if(cameraIndex==1) {
                Mat mapMatrix = Imgproc.getRotationMatrix2D(center, angle, scale);
                Imgproc.warpAffine(rgba, dst, mapMatrix, new Size(rgba.width(), rgba.height()));
                Core.flip(dst,rgba,1);
                dst = rgba;
            }else{
                Mat mapMatrix = Imgproc.getRotationMatrix2D(center, angle1, scale);
                Imgproc.warpAffine(rgba, dst, mapMatrix, new Size(rgba.width(), rgba.height()));

            }
            return dst;

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        mItemType = menu.add(mDetectorName[mDetectorType]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);
        else if (item == mItemType) {
            int tmpDetectorType = (mDetectorType + 1) % mDetectorName.length;
            item.setTitle(mDetectorName[tmpDetectorType]);
            setDetectorType(tmpDetectorType);
        }
        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void setDetectorType(int type) {
        if (mDetectorType != type) {
            mDetectorType = type;

            if (type == NATIVE_DETECTOR) {
                Log.i(TAG, "Detection Based Tracker enabled");
                mNativeDetector.start();
            } else {
                Log.i(TAG, "Cascade detector enabled");
                mNativeDetector.stop();
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

        switch (seekBar.getId()) {
            case R.id.param1seekbar:
                param1 = i + 1;
                tvParam1.setText(param1 + "");
                break;
            case R.id.param2seekbar:
                param2 = i + 1;
                tvParam2.setText(param2 + "");
                break;
        }

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
