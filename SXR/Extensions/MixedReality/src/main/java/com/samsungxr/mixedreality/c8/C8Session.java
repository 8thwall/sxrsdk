/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsungxr.mixedreality.c8;

import android.app.Activity;
import android.graphics.Bitmap;
import android.opengl.Matrix;
import android.util.DisplayMetrics;
import android.view.Surface;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import com.samsungxr.SXRCameraRig;
import com.samsungxr.SXRContext;
import com.samsungxr.SXRDrawFrameListener;
import com.samsungxr.SXRExternalTexture;
import com.samsungxr.SXRMaterial;
import com.samsungxr.SXRMesh;
import com.samsungxr.SXRMeshCollider;
import com.samsungxr.SXRNode;
import com.samsungxr.SXRPerspectiveCamera;
import com.samsungxr.SXRPicker;
import com.samsungxr.SXRRenderData;
import com.samsungxr.SXRScene;
import com.samsungxr.SXRNode;
import com.samsungxr.SXRTexture;
import com.samsungxr.mixedreality.CameraPermissionHelper;
import com.samsungxr.mixedreality.SXRAnchor;
import com.samsungxr.mixedreality.SXRMarker;
import com.samsungxr.mixedreality.SXRHitResult;
import com.samsungxr.mixedreality.SXRLightEstimate;
import com.samsungxr.mixedreality.SXRPlane;
import com.samsungxr.mixedreality.SXRTrackingState;
import com.samsungxr.mixedreality.IAnchorEvents;
import com.samsungxr.mixedreality.IMarkerEvents;
import com.samsungxr.mixedreality.IMixedReality;
import com.samsungxr.mixedreality.IPlaneEvents;
import com.samsungxr.mixedreality.MRCommon;
import com.samsungxr.utility.Log;

import org.capnproto.MessageBuilder;

import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.the8thwall.reality.app.xr.android.XREngine;
import com.the8thwall.reality.engine.api.Reality.CoordinateSystemConfiguration;
import com.the8thwall.reality.engine.api.Reality.RealityResponse;
import com.the8thwall.reality.engine.api.Reality.XRAppEnvironment;
import com.the8thwall.reality.engine.api.Reality.XRConfiguration;
import com.the8thwall.reality.engine.api.Reality.XREnvironment;



public class C8Session extends MRCommon {
  static { System.loadLibrary("xr8"); }

    public static final String TAG = "8thWall";

    private static float AR2VR_SCALE = 100.0f;

    private Session mSession;
    private boolean mInstallRequested;
    private Config mConfig;

    private SXRScene mVRScene;
    private SXRNode mARPassThroughObject;
    private Frame mLastARFrame;
    private Frame arFrame;
    private C8Handler mC8Handler;
    private boolean mEnableCloudAnchor;
    private Vector2f mScreenToCamera = new Vector2f(1, 1);

    private SXRContext mGvrContext;
    private Map<Plane, C8Plane> mArPlanes;
    private Map<AugmentedImage, C8Marker> mArAugmentedImages;
    private List<C8Anchor> mArAnchors;

    private XREngine xr_;
    private long realityMicros_ = 0;
    private XRAppEnvironment.Reader xrAppEnv_;


    private C8Plane mGroundPlane;

    /* From AR to SXR space matrices */
    private float[] mSXRCamMatrix = new float[16];

    private Vector3f mDisplayGeometry;

    private float mScreenDepth;

    private final Map<Anchor, CloudAnchorCallback> pendingAnchors = new HashMap<>();

    public C8Session(SXRScene scene, boolean enableCloudAnchor) {
        super(scene.getSXRContext());
        mSession = null;
        mLastARFrame = null;
        mVRScene = scene;
        mEnableCloudAnchor = enableCloudAnchor;
        mGroundPlane = new C8Plane(scene.getSXRContext(), 1, 1);


        mGvrContext = scene.getSXRContext();
        mArPlanes = new HashMap<>();
        mArAugmentedImages = new HashMap<>();
        mArAnchors = new ArrayList<>();
    }

    @Override
    public float getARToVRScale() { return AR2VR_SCALE; }

    @Override
    public float getScreenDepth()
    {
        return mScreenDepth;
    }

    @Override
    protected void onResume() {

        Log.d(TAG, "onResumeAR");

        if (xr_ == null) {
          XREngine.create(mSXRContext.getContext(), 1 /* OPENGL */);
          xr_ = XREngine.getInstance();
          xrAppEnv_ = xr_.getXRAppEnvironmentReader();

          XRConfiguration.Builder config = new MessageBuilder().getRoot(XRConfiguration.factory);
          config.getCoordinateConfiguration().getOrigin().getPosition().setY(1.65f);
          config.getCoordinateConfiguration().setAxes(CoordinateSystemConfiguration.CoordinateAxes.X_LEFT_Y_UP_Z_FORWARD);
          config.getCoordinateConfiguration().setScale(1.65f);
          xr_.configure(config.asReader());
        }

        /*
        if (mSession == null) {

            if (!checkC8AndCamera()) {
                return;
            }

            // Create default config and check if supported.
            mConfig = new Config(mSession);
            if (mEnableCloudAnchor) {
                mConfig.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
            }
            mConfig.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            ArCoreApk arCoreApk = ArCoreApk.getInstance();
            ArCoreApk.Availability availability = arCoreApk.checkAvailability(mSXRContext.getContext());
            if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
                showSnackbarMessage("This device does not support AR", true);
            }
            mSession.configure(mConfig);
        }
        */

        showLoadingMessage();

        /*
        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        */

        mSXRContext.runOnGlThread(new Runnable() {
            @Override
            public void run() {
                try {
                    onInitC8Session(mSXRContext);
                } catch (CameraNotAvailableException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");

        if (mSession != null) {
            mSession.pause();
        }
    }

    private boolean checkC8AndCamera() {
        Activity activity = mSXRContext.getApplication().getActivity();
        Exception exception = null;
        String message = null;
        try {
            switch (ArCoreApk.getInstance().requestInstall(activity, !mInstallRequested)) {
                case INSTALL_REQUESTED:
                    mInstallRequested = true;
                    return false;
                case INSTALLED:
                    break;
            }

            // C8 requires camera permissions to operate. If we did not yet obtain runtime
            // permission on Android M and above, now is a good time to ask the user for it.
            if (!CameraPermissionHelper.hasCameraPermission(activity)) {
                CameraPermissionHelper.requestCameraPermission(activity);
                return false;
            }

            mSession = new Session(/* context= */ activity);
        } catch (UnavailableArcoreNotInstalledException
                | UnavailableUserDeclinedInstallationException e) {
            message = "Please install C8";
            exception = e;
        } catch (UnavailableApkTooOldException e) {
            message = "Please update C8";
            exception = e;
        } catch (UnavailableSdkTooOldException e) {
            message = "Please update this app";
            exception = e;
        } catch (Exception e) {
            message = "This device does not support AR";
            exception = e;
        }

        if (message != null) {
            showSnackbarMessage(message, true);
            android.util.Log.e(TAG, "Exception creating session", exception);
            return false;
        }

        return true;
    }

    private void showSnackbarMessage(String message, boolean finishOnDismiss) {
        Log.d(TAG, message);
        if (finishOnDismiss) {
            //FIXME: finish();
        }
    }

    private void showLoadingMessage() {
        showSnackbarMessage("Searching for surfaces...", false);
    }

    private void onInitC8Session(SXRContext gvrContext) throws CameraNotAvailableException {
        xr_.resume();
        mC8Handler = new C8Handler();
        gvrContext.registerDrawFrameListener(mC8Handler);
        configDisplayAspectRatio(mSXRContext.getActivity());
        /*

        SXRTexture passThroughTexture = new SXRExternalTexture(gvrContext);

        mSession.setCameraTextureName(passThroughTexture.getId());


        mLastARFrame = mSession.update();
        final SXRCameraRig cameraRig = mVRScene.getMainCameraRig();

        mDisplayGeometry = configDisplayGeometry(mLastARFrame.getCamera(), cameraRig);
        mSession.setDisplayGeometry(Surface.ROTATION_90,
                (int) mDisplayGeometry.x, (int) mDisplayGeometry.y);

        final SXRMesh mesh = SXRMesh.createQuad(mSXRContext, "float3 a_position float2 a_texcoord",
                mDisplayGeometry.x, mDisplayGeometry.y);

        final FloatBuffer texCoords = mesh.getTexCoordsAsFloatBuffer();
        final int capacity = texCoords.capacity();
        final int FLOAT_SIZE = 4;

        ByteBuffer bbTexCoordsTransformed = ByteBuffer.allocateDirect(capacity * FLOAT_SIZE);
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder());

        FloatBuffer quadTexCoordTransformed = bbTexCoordsTransformed.asFloatBuffer();

        mLastARFrame.transformDisplayUvCoords(texCoords, quadTexCoordTransformed);

        float[] uv = new float[capacity];
        quadTexCoordTransformed.get(uv);

        mesh.setTexCoords(uv);
        */

        /* To render texture from phone's camera */
        /*
        mARPassThroughObject = new SXRNode(gvrContext, mesh,
                passThroughTexture, SXRMaterial.SXRShaderType.OES.ID);

        mARPassThroughObject.getRenderData().setRenderingOrder(SXRRenderData.SXRRenderingOrder.BACKGROUND);
        mARPassThroughObject.getRenderData().setDepthTest(false);
        mARPassThroughObject.getTransform().setPosition(0, 0, mDisplayGeometry.z);
        mARPassThroughObject.attachComponent(new SXRMeshCollider(gvrContext, true));
        mARPassThroughObject.setName("ARPassThrough");
        mVRScene.getMainCameraRig().addChildObject(mARPassThroughObject);
        */
        /* AR main loop */
        /*
        syncARCamToVRCam(mLastARFrame.getCamera(), cameraRig);
        */

        gvrContext.getEventManager().sendEvent(this,
                IPlaneEvents.class,
                "onStartPlaneDetection",
                this);

        notifyPlaneDetectionListeners(mGroundPlane);
        notifyPlaneStateChangeListeners(mGroundPlane, SXRTrackingState.TRACKING);
    }

    public class C8Handler implements SXRDrawFrameListener {
        @Override
        public void onDrawFrame(float v) {
            RealityResponse.Reader r = xr_.getCurrentRealityXRReader();
            xr_.renderFrameForDisplay();

            if (r.getEventId().getEventTimeMicros() == realityMicros_) {
                // FIXME: C8 works at 30fps.
                return;
            }

            realityMicros_ = r.getEventId().getEventTimeMicros();

            syncARCamToVRCam(r, mVRScene.getMainCameraRig());

            updatePlanes(r, AR2VR_SCALE);


            /*

            mSXRContext.getEventManager().sendEvent(this,
                    IPlaneEvents.class,
                    "onPlaneDetected",
                    mGroundPlane);
                    */

            /*
            try {
                arFrame = mSession.update();
            } catch (CameraNotAvailableException e) {
                e.printStackTrace();
                mSXRContext.unregisterDrawFrameListener(this);
                mSXRContext.getEventManager().sendEvent(this,
                        IPlaneEvents.class,
                        "onStopPlaneDetection",
                        this);
                return;
            }

            Camera arCamera = arFrame.getCamera();

            if (arFrame.getTimestamp() == mLastARFrame.getTimestamp()) {
                // FIXME: C8 works at 30fps.
                return;
            }


            syncARCamToVRCam(arCamera, mVRScene.getMainCameraRig());

            if (arCamera.getTrackingState() != TrackingState.TRACKING) {
                return;
            }

            updatePlanes(mSession.getAllTrackables(Plane.class), AR2VR_SCALE);

            updateAugmentedImages(arFrame.getUpdatedTrackables(AugmentedImage.class));

            updateAnchors(AR2VR_SCALE);

            updateCloudAnchors(arFrame.getUpdatedAnchors());

            mLastARFrame = arFrame;
            */
        }
    }

    private void syncARCamToVRCam(RealityResponse.Reader r, SXRCameraRig cameraRig) {
        float w_ = r.getXRResponse().getCamera().getExtrinsic().getRotation().getW();
        float x_ = r.getXRResponse().getCamera().getExtrinsic().getRotation().getX();
        float y_ = r.getXRResponse().getCamera().getExtrinsic().getRotation().getY();
        float z_ = r.getXRResponse().getCamera().getExtrinsic().getRotation().getZ();

        float px = r.getXRResponse().getCamera().getExtrinsic().getPosition().getX();
        float py = r.getXRResponse().getCamera().getExtrinsic().getPosition().getY();
        float pz = r.getXRResponse().getCamera().getExtrinsic().getPosition().getZ();

        float x = mSXRCamMatrix[12];
        float y = mSXRCamMatrix[13];
        float z = mSXRCamMatrix[14];

        double wx = w_ * x_;
        double wy = w_ * y_;
        double wz = w_ * z_;
        double xx = x_ * x_;
        double xy = x_ * y_;
        double xz = x_ * z_;
        double yy = y_ * y_;
        double yz = y_ * z_;
        double zz = z_ * z_;

        float mm00 = (float)(1.0 - 2.0 * (yy + zz));
        float mm01 = (float)(2.0000000 * (xy - wz));
        float mm02 = (float)(2.0000000 * (xz + wy));

        float mm10 = (float)(2.0000000 * (xy + wz));
        float mm11 = (float)(1.0 - 2.0 * (xx + zz));
        float mm12 = (float)(2.0000000 * (yz - wx));

        float mm20 = (float)(2.0000000 * (xz - wy));
        float mm21 = (float)(2.0000000 * (yz + wx));
        float mm22 = (float)(1.0 - 2.0 * (xx + yy));

        mSXRCamMatrix[0] = mm00;
        mSXRCamMatrix[1] = mm10;
        mSXRCamMatrix[2] = mm20;
        mSXRCamMatrix[3] = 0.0f;

        mSXRCamMatrix[4] = mm01;
        mSXRCamMatrix[5] = mm11;
        mSXRCamMatrix[6] = mm21;
        mSXRCamMatrix[7] = 0.0f;

        mSXRCamMatrix[8] = mm02;
        mSXRCamMatrix[9] = mm12;
        mSXRCamMatrix[10] = mm22;
        mSXRCamMatrix[11] = 0.0f;

        mSXRCamMatrix[12] = px;
        mSXRCamMatrix[13] = py;
        mSXRCamMatrix[14] = pz;

        mSXRCamMatrix[15] = 1.0f;

        Log.d(TAG,
            String.format("Setting main camera to (qw: %f, qx: %f, qy: %f, qz: %f); (%f, %f, %f)",
                w_, x_, y_, z_, px, py, pz));

        /*
        arCamera.getDisplayOrientedPose().toMatrix(mSXRCamMatrix, 0);
        */

        /*
        // FIXME: This is a workaround because the AR camera's pose is changing its
        // position values even if it is stopped! To avoid the scene looks trembling
        mSXRCamMatrix[12] = (mSXRCamMatrix[12] * AR2VR_SCALE + x) * 0.5f;
        mSXRCamMatrix[13] = (mSXRCamMatrix[13] * AR2VR_SCALE + y) * 0.5f;
        mSXRCamMatrix[14] = (mSXRCamMatrix[14] * AR2VR_SCALE + z) * 0.5f;
        */

        cameraRig.getTransform().setModelMatrix(mSXRCamMatrix);

        mDisplayGeometry = configDisplayGeometry(r, cameraRig);
    }

    private void configDisplayAspectRatio(Activity activity) {
        final DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        mScreenToCamera.x = metrics.widthPixels;
        mScreenToCamera.y = metrics.heightPixels;

        XRConfiguration.Builder config = new MessageBuilder().getRoot(XRConfiguration.factory);
        config.getMask().setCamera(true);
        config.getGraphicsIntrinsics().setTextureWidth(metrics.widthPixels);
        config.getGraphicsIntrinsics().setTextureHeight(metrics.heightPixels);
        config.getGraphicsIntrinsics().setNearClip(0.03f);
        config.getGraphicsIntrinsics().setFarClip(1000.0f);
        config.getGraphicsIntrinsics().setDigitalZoomVertical(1.0f);
        config.getGraphicsIntrinsics().setDigitalZoomHorizontal(1.0f);
        xr_.configure(config.asReader());
    }

    private Vector3f configDisplayGeometry(RealityResponse.Reader r, SXRCameraRig cameraRig) {
        SXRPerspectiveCamera centerCamera = cameraRig.getCenterCamera();
        float near = centerCamera.getNearClippingDistance();
        float far = centerCamera.getFarClippingDistance();

        // Get phones' cam projection matrix.
        float[] m = new float[16];
        for (int i = 0; i < 16; ++i) {
            m[i] = r.getXRResponse().getCamera().getIntrinsic().getMatrix44f().get(i);
        }

        Matrix4f projmtx = new Matrix4f();
        projmtx.set(m);

        float aspectRatio = projmtx.m11() / projmtx.m00();
        float arCamFOV = projmtx.perspectiveFov();
        float tanfov =  (float) Math.tan(arCamFOV * 0.5f);
        float quadDistance = far - 1;
        float quadHeight = quadDistance * tanfov * 2;
        float quadWidth = quadHeight * aspectRatio;

        // Use the same fov from AR to VR Camera as default value.
        float vrFov = (float) Math.toDegrees(arCamFOV);
        setVRCameraFov(cameraRig, vrFov);

        // VR Camera will be updated by AR pose, not by internal sensors.
        cameraRig.getHeadTransform().setRotation(1, 0, 0, 0);
        cameraRig.setCameraRigType(SXRCameraRig.SXRCameraRigType.Freeze.ID);

        android.util.Log.d(TAG, "C8 configured to: passthrough[w: "
                + quadWidth + ", h: " + quadHeight +", z: " + quadDistance
                + "], cam fov: " +vrFov + ", aspect ratio: " + aspectRatio);
        mScreenToCamera.x = quadWidth / mScreenToCamera.x;    // map [0, ScreenSize] to [-Display, +Display]
        mScreenToCamera.y = quadHeight / mScreenToCamera.y;
        mScreenDepth = quadHeight / tanfov;
        return new Vector3f(quadWidth, quadHeight, -quadDistance);
    }

    private static void setVRCameraFov(SXRCameraRig camRig, float degreesFov) {
        camRig.getCenterCamera().setFovY(degreesFov);
        ((SXRPerspectiveCamera)camRig.getLeftCamera()).setFovY(degreesFov);
        ((SXRPerspectiveCamera)camRig.getRightCamera()).setFovY(degreesFov);
    }

    @Override
    protected SXRNode onGetPassThroughObject() {
        return mARPassThroughObject;
    }

    @Override
    protected ArrayList<SXRPlane> onGetAllPlanes() {
        ArrayList<SXRPlane> planes = new ArrayList<SXRPlane>();
        planes.add(mGroundPlane);
        return planes;
    }

    @Override
    protected SXRAnchor onCreateAnchor(float[] pose) {
        /*
        final float[] translation = new float[3];
        final float[] rotation = new float[4];
        final float[] arPose = pose.clone();

        gvr2ar(arPose);

        convertMatrixPoseToVector(arPose, translation, rotation);

        Anchor anchor = mSession.createAnchor(new Pose(translation, rotation));
        return createAnchor(anchor, AR2VR_SCALE);
        */
        return null;
    }

    @Override
    protected void onUpdateAnchorPose(SXRAnchor anchor, float[] pose) {
       /*
        final float[] translation = new float[3];
        final float[] rotation = new float[4];
        final float[] arPose = pose.clone();

        gvr2ar(arPose);

        convertMatrixPoseToVector(arPose, translation, rotation);

        Anchor arAnchor = mSession.createAnchor(new Pose(translation, rotation));
        updateAnchorPose((C8Anchor) anchor, arAnchor);
        */
    }

    @Override
    protected void onRemoveAnchor(SXRAnchor anchor) {
        /*
        removeAnchor((C8Anchor) anchor);
        */
    }

    /**
     * This method hosts an anchor. The {@code listener} will be invoked when the results are
     * available.
     */
    @Override
    synchronized protected void onHostAnchor(SXRAnchor anchor, CloudAnchorCallback cb) {
        /*
        Anchor newAnchor = mSession.hostCloudAnchor(((C8Anchor) anchor).getAnchorAR());
        pendingAnchors.put(newAnchor, cb);
        */
    }

    /**
     * This method resolves an anchor. The {@link IAnchorEvents} will be invoked when the results are
     * available.
     */
    synchronized protected void onResolveCloudAnchor(String anchorId, CloudAnchorCallback cb) {
        /*
        Anchor newAnchor = mSession.resolveCloudAnchor(anchorId);
        pendingAnchors.put(newAnchor, cb);
        */
    }

    /**
     * Should be called with the updated anchors available after a {@link Session#update()} call.
     */
    synchronized void updateCloudAnchors(Collection<Anchor> updatedAnchors) {
        /*
        for (Anchor anchor : updatedAnchors) {
            if (pendingAnchors.containsKey(anchor)) {
                Anchor.CloudAnchorState cloudState = anchor.getCloudAnchorState();
                if (isReturnableState(cloudState)) {
                    CloudAnchorCallback cb = pendingAnchors.get(anchor);
                    pendingAnchors.remove(anchor);
                    SXRAnchor newAnchor = createAnchor(anchor, AR2VR_SCALE);
                    cb.onCloudUpdate(newAnchor);
                }
            }
        }
        */
    }

    /**
     * Used to clear any currently registered listeners, so they wont be called again.
     */
    synchronized void clearListeners() {
        /*
        pendingAnchors.clear();
        */
    }

    private static boolean isReturnableState(Anchor.CloudAnchorState cloudState) {
        return false;
        /*
        switch (cloudState) {
            case NONE:
            case TASK_IN_PROGRESS:
                return false;
            default:
                return true;
        }
        */
    }

    @Override
    protected void onSetEnableCloudAnchor(boolean enableCloudAnchor) {
        /*
        mEnableCloudAnchor = enableCloudAnchor;
        */
    }

    @Override
    protected SXRHitResult onHitTest(SXRPicker.SXRPickedObject collision) {
        return null;
        /*
        Vector2f tapPosition = convertToDisplayGeometrySpace(collision.hitLocation[0], collision.hitLocation[1]);
        List<HitResult> hitResult = arFrame.hitTest(tapPosition.x, tapPosition.y);

        return hitTest(hitResult, AR2VR_SCALE);
        */
    }

    @Override
    protected SXRHitResult onHitTest(float x, float y) {
        return null;
        /*
        x *= mScreenToCamera.x;
        y *= mScreenToCamera.y;
        List<HitResult> hitResult = arFrame.hitTest(x, y);
        return hitTest(hitResult, AR2VR_SCALE);
        */
    }

    @Override
    protected SXRLightEstimate onGetLightEstimate() {
        return getLightEstimate(); // arFrame.getLightEstimate());
    }

    @Override
    protected void onSetMarker(Bitmap image) {
        /*
        ArrayList<Bitmap> imagesList = new ArrayList<>();
        imagesList.add(image);
        onSetMarkers(imagesList);
        */
    }

    @Override
    protected void onSetMarkers(ArrayList<Bitmap> imagesList) {
        /*
        AugmentedImageDatabase augmentedImageDatabase = new AugmentedImageDatabase(mSession);
        for (Bitmap image : imagesList) {
            augmentedImageDatabase.addImage("image_name", image);
        }

        mConfig.setAugmentedImageDatabase(augmentedImageDatabase);
        mSession.configure(mConfig);
        */
    }

    @Override
    protected ArrayList<SXRMarker> onGetAllMarkers() {
        return new ArrayList<SXRMarker>();
        // return getAllMarkers();
    }

    @Override
    protected float[] onMakeInterpolated(float[] poseA, float[] poseB, float t) {
        float[] translation = new float[3];
        float[] rotation = new float[4];
        float[] newMatrixPose = new float[16];

        convertMatrixPoseToVector(poseA, translation, rotation);
        Pose ARPoseA = new Pose(translation, rotation);

        convertMatrixPoseToVector(poseB, translation, rotation);
        Pose ARPoseB = new Pose(translation, rotation);

        Pose newPose = Pose.makeInterpolated(ARPoseA, ARPoseB, t);
        newPose.toMatrix(newMatrixPose, 0);

        return newMatrixPose;
    }

    private Vector2f convertToDisplayGeometrySpace(float x, float y) {
        final float hitX = x + 0.5f * mDisplayGeometry.x;
        final float hitY = 0.5f * mDisplayGeometry.y - y;

        return new Vector2f(hitX, hitY);
    }

    static void gvr2ar(float[] transformModelMatrix) {
        Matrix.scaleM(transformModelMatrix, 0, 1/AR2VR_SCALE, 1/AR2VR_SCALE, 1/AR2VR_SCALE);

        transformModelMatrix[12] /= AR2VR_SCALE;
        transformModelMatrix[13] /= AR2VR_SCALE;
        transformModelMatrix[14] /= AR2VR_SCALE;
    }

    static void convertMatrixPoseToVector(float[] pose, float[] translation, float[] rotation) {
        Vector3f vectorTranslation = new Vector3f();
        Quaternionf quaternionRotation = new Quaternionf();
        Matrix4f matrixPose = new Matrix4f();

        matrixPose.set(pose);

        matrixPose.getTranslation(vectorTranslation);
        translation[0] = vectorTranslation.x;
        translation[1] = vectorTranslation.y;
        translation[2] = vectorTranslation.z;

        matrixPose.getNormalizedRotation(quaternionRotation);
        rotation[0] = quaternionRotation.x;
        rotation[1] = quaternionRotation.y;
        rotation[2] = quaternionRotation.z;
        rotation[3] = quaternionRotation.w;
    }

    public void updatePlanes(RealityResponse.Reader r, float scale) {

        /*

        // Don't update planes (or notify) when the plane listener is empty, i.e., there is
        // no listener registered.
        C8Plane arCorePlane;

        for (Plane plane: allPlanes) {
            if (plane.getTrackingState() != TrackingState.TRACKING
                    || mArPlanes.containsKey(plane)) {
                continue;
            }

            arCorePlane = createPlane(plane);
            // FIXME: New planes are updated two times
            arCorePlane.update(scale);
            notifyPlaneDetectionListeners(arCorePlane);
        }

        for (Plane plane: mArPlanes.keySet()) {
            arCorePlane = mArPlanes.get(plane);

            if (plane.getTrackingState() == TrackingState.TRACKING &&
                    arCorePlane.getTrackingState() != SXRTrackingState.TRACKING) {
                arCorePlane.setTrackingState(SXRTrackingState.TRACKING);
                notifyPlaneStateChangeListeners(arCorePlane, SXRTrackingState.TRACKING);
            }
            else if (plane.getTrackingState() == TrackingState.PAUSED &&
                    arCorePlane.getTrackingState() != SXRTrackingState.PAUSED) {
                arCorePlane.setTrackingState(SXRTrackingState.PAUSED);
                notifyPlaneStateChangeListeners(arCorePlane, SXRTrackingState.PAUSED);
            }
            else if (plane.getTrackingState() == TrackingState.STOPPED &&
                    arCorePlane.getTrackingState() != SXRTrackingState.STOPPED) {
                arCorePlane.setTrackingState(SXRTrackingState.STOPPED);
                notifyPlaneStateChangeListeners(arCorePlane, SXRTrackingState.STOPPED);
            }

            if (plane.getSubsumedBy() != null && arCorePlane.getParentPlane() == null) {
                arCorePlane.setParentPlane(mArPlanes.get(plane.getSubsumedBy()));
                notifyMergedPlane(arCorePlane, arCorePlane.getParentPlane());
            }

            arCorePlane.update(scale);
        }
        */
    }

    public void updateAugmentedImages(Collection<AugmentedImage> allAugmentedImages){
        /*
        C8Marker arCoreMarker;

        for (AugmentedImage augmentedImage: allAugmentedImages) {
            if (augmentedImage.getTrackingState() != TrackingState.TRACKING
                || mArAugmentedImages.containsKey(augmentedImage)) {
                continue;
            }

            arCoreMarker = createMarker(augmentedImage);
            notifyMarkerDetectionListeners(arCoreMarker);

            mArAugmentedImages.put(augmentedImage, arCoreMarker);
        }

        for (AugmentedImage augmentedImage: mArAugmentedImages.keySet()) {
            arCoreMarker = mArAugmentedImages.get(augmentedImage);

            if (augmentedImage.getTrackingState() == TrackingState.TRACKING &&
                    arCoreMarker.getTrackingState() != SXRTrackingState.TRACKING) {
                arCoreMarker.setTrackingState(SXRTrackingState.TRACKING);
                notifyMarkerStateChangeListeners(arCoreMarker, SXRTrackingState.TRACKING);
            }
            else if (augmentedImage.getTrackingState() == TrackingState.PAUSED &&
                    arCoreMarker.getTrackingState() != SXRTrackingState.PAUSED) {
                arCoreMarker.setTrackingState(SXRTrackingState.PAUSED);
                notifyMarkerStateChangeListeners(arCoreMarker, SXRTrackingState.PAUSED);
            }
            else if (augmentedImage.getTrackingState() == TrackingState.STOPPED &&
                    arCoreMarker.getTrackingState() != SXRTrackingState.STOPPED) {
                arCoreMarker.setTrackingState(SXRTrackingState.STOPPED);
                notifyMarkerStateChangeListeners(arCoreMarker, SXRTrackingState.STOPPED);
            }
        }
        */
    }

    public void updateAnchors(float scale) {
        /*
        for (C8Anchor anchor: mArAnchors) {
            Anchor arAnchor = anchor.getAnchorAR();

            if (arAnchor.getTrackingState() == TrackingState.TRACKING &&
                    anchor.getTrackingState() != SXRTrackingState.TRACKING) {
                anchor.setTrackingState(SXRTrackingState.TRACKING);
                notifyAnchorStateChangeListeners(anchor, SXRTrackingState.TRACKING);
            }
            else if (arAnchor.getTrackingState() == TrackingState.PAUSED &&
                    anchor.getTrackingState() != SXRTrackingState.PAUSED) {
                anchor.setTrackingState(SXRTrackingState.PAUSED);
                notifyAnchorStateChangeListeners(anchor, SXRTrackingState.PAUSED);
            }
            else if (arAnchor.getTrackingState() == TrackingState.STOPPED &&
                    anchor.getTrackingState() != SXRTrackingState.STOPPED) {
                anchor.setTrackingState(SXRTrackingState.STOPPED);
                notifyAnchorStateChangeListeners(anchor, SXRTrackingState.STOPPED);
            }

            anchor.update(scale);
        }
        */
    }

    public ArrayList<SXRPlane> getAllPlanes() {
        ArrayList<SXRPlane> allPlanes = new ArrayList<>();

        /*
        for (Plane plane: mArPlanes.keySet()) {
            allPlanes.add(mArPlanes.get(plane));
        }
        */

        return allPlanes;
    }

    public ArrayList<SXRMarker> getAllMarkers() {
        ArrayList<SXRMarker> allAugmentedImages = new ArrayList<>();
        /*

        for (AugmentedImage augmentedImage: mArAugmentedImages.keySet()) {
            allAugmentedImages.add(mArAugmentedImages.get(augmentedImage));
        }
        */

        return allAugmentedImages;
    }

    public C8Plane createPlane(Plane plane) {
        C8Plane arCorePlane = new C8Plane(mGvrContext, 1, 1);
        mArPlanes.put(plane, arCorePlane);
        return arCorePlane;
    }

    public C8Marker createMarker(AugmentedImage augmentedImage) {
        return null;
        // C8Marker arCoreMarker = new C8Marker(augmentedImage);
        // return arCoreMarker;
    }

    public SXRAnchor createAnchor(Anchor arAnchor, float scale) {
        return null;
        /*
        C8Anchor arCoreAnchor = new C8Anchor(mGvrContext);
        arCoreAnchor.setAnchorAR(arAnchor);
        mArAnchors.add(arCoreAnchor);
        arCoreAnchor.update(scale);
        return arCoreAnchor;
        */
    }

    public void updateAnchorPose(C8Anchor anchor, Anchor arAnchor) {
        /*
        if (anchor.getAnchorAR() != null) {
            anchor.getAnchorAR().detach();
        }
        anchor.setAnchorAR(arAnchor);
        */
    }

    public void removeAnchor(C8Anchor anchor) {
        /*
        anchor.getAnchorAR().detach();
        mArAnchors.remove(anchor);
        SXRNode anchorNode = anchor.getOwnerObject();
        SXRNode anchorParent = anchorNode.getParent();
        anchorParent.removeChildObject(anchorNode);
        */
    }

    public SXRHitResult hitTest(List<HitResult> hitResult, float scale) {
        return null;
        /*
        for (HitResult hit : hitResult) {
            // Check if any plane was hit, and if it was hit inside the plane polygon
            Trackable trackable = hit.getTrackable();
            // Creates an anchor if a plane or an oriented point was hit.
            if ((trackable instanceof Plane
                    && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                    && ((Plane) trackable).getSubsumedBy() == null) {
                SXRHitResult gvrHitResult = new SXRHitResult();
                float[] hitPose = new float[16];

                hit.getHitPose().toMatrix(hitPose, 0);
                // Convert the value from C8 to SXRf and set the pose
                ar2gvr(hitPose, scale);
                gvrHitResult.setPose(hitPose);
                // TODO: this distance is using C8 values, change it to use SXRf instead
                gvrHitResult.setDistance(hit.getDistance());
                gvrHitResult.setPlane(mArPlanes.get(trackable));

                return gvrHitResult;
            }
        }

        return null;
        */
    }

    /**
     * Converts from AR world space to SXRf world space.
     */
    private void ar2gvr(float[] poseMatrix, float scale) {
        // Real world scale
        Matrix.scaleM(poseMatrix, 0, scale, scale, scale);
        poseMatrix[12] = poseMatrix[12] * scale;
        poseMatrix[13] = poseMatrix[13] * scale;
        poseMatrix[14] = poseMatrix[14] * scale;
    }

    public SXRLightEstimate getLightEstimate(/*LightEstimate lightEstimate*/) {
        C8LightEstimate arCoreLightEstimate = new C8LightEstimate();
        SXRLightEstimate.SXRLightEstimateState state;

        arCoreLightEstimate.setPixelIntensity(0.5f);// lightEstimate.getPixelIntensity());
        state = SXRLightEstimate.SXRLightEstimateState.VALID;
        arCoreLightEstimate.setState(state);

        return arCoreLightEstimate;
    }

    private void notifyPlaneDetectionListeners(SXRPlane plane) {
        mGvrContext.getEventManager().sendEvent(this,
                IPlaneEvents.class,
                "onPlaneDetected",
                plane);
    }

    private void notifyPlaneStateChangeListeners(SXRPlane plane, SXRTrackingState trackingState) {
        mGvrContext.getEventManager().sendEvent(this,
                IPlaneEvents.class,
                "onPlaneStateChange",
                plane,
                trackingState);
    }

    private void notifyMergedPlane(SXRPlane childPlane, SXRPlane parentPlane) {
        mGvrContext.getEventManager().sendEvent(this,
                IPlaneEvents.class,
                "onPlaneMerging",
                childPlane,
                parentPlane);
    }

    private void notifyAnchorStateChangeListeners(SXRAnchor anchor, SXRTrackingState trackingState) {
        mGvrContext.getEventManager().sendEvent(this,
                IAnchorEvents.class,
                "onAnchorStateChange",
                anchor,
                trackingState);
    }

    private void notifyMarkerDetectionListeners(SXRMarker image) {
        mGvrContext.getEventManager().sendEvent(this,
                IMarkerEvents.class,
                "onMarkerDetected",
                image);
    }

    private void notifyMarkerStateChangeListeners(SXRMarker image, SXRTrackingState trackingState) {
        mGvrContext.getEventManager().sendEvent(this,
                IMarkerEvents.class,
                "onMarkerStateChange",
                image,
                trackingState);
    }
}
