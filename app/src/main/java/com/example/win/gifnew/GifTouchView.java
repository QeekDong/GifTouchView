package com.example.win.gifnew;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.DrawableUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.text.NumberFormat;

/**
 * Created by win on 2017/10/18.
 */

public class GifTouchView extends View{
    private static  final  String TAG = "GifTouchView";
    private float downX;
    private float downY;
    private float firstX;
    private float firstY;
    private OnClickListener listener;
    private boolean clickable = true;
    private float whRatio;
    private int minWidth;
    private int maxWidth;
    private int minHeight;
    private int maxHeight;

    private static final int DEFAULT_MOVIE_VIEW_DURATION = 1000;

    private int mMovieResourceId;
    private Movie movie;

    private long mMovieStart;
    private int mCurrentAnimationTime;
    private Drawable mPausedDrawable;
    private float indexFrame;



    /**
     * Position for drawing animation frames in the center of the view.
     */
    private float mLeft;
    private float mTop;

    /**
     * Scaling factor to fit the animation within view bounds.
     */
    private float mScale;

    /**
     * Scaled movie frames width and height.
     */
    private int mMeasuredMovieWidth;
    private int mMeasuredMovieHeight;

    private volatile boolean mPaused;
    private boolean mVisible = true;
    private GifImageDecoder gifImageDecoder;

    private float lastDis;
    private float coreX;
    private float coreY;
    private boolean doubleMove = false;

    public GifTouchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setViewAttributes(context, attrs, defStyleAttr);
    }

    public GifTouchView(Context context, AttributeSet attrs) {
        this(context, attrs, R.styleable.CustomTheme_gifViewStyle);
    }

    public GifTouchView(Context context) {
        this(context, null);
    }

    private void setViewAttributes(Context context, AttributeSet attrs, int defStyle) {

        /**
         * Starting from HONEYCOMB(Api Level:11) have to turn off HW acceleration to draw
         * Movie on Canvas.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        final TypedArray array = context.obtainStyledAttributes(attrs,
                R.styleable.GifView, defStyle, R.style.Widget_GifView);

        //-1 is default value
        mMovieResourceId = array.getResourceId(R.styleable.GifView_gif, -1);
        mPaused = array.getBoolean(R.styleable.GifView_paused, false);

        array.recycle();

        if (mMovieResourceId != -1) {
            movie = Movie.decodeStream(getResources().openRawResource(mMovieResourceId));

        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (movie != null) {
            int movieWidth = movie.width();
            int movieHeight = movie.height();
            Log.d(TAG, "onMeasure: movieWidth " + movieWidth);
            Log.d(TAG, "onMeasure: movieHeight " + movieHeight);
            /*
             * Calculate horizontal scaling
			 */
            float scaleH = 1f;
            int measureModeWidth = MeasureSpec.getMode(widthMeasureSpec);
            Log.d(TAG, "onMeasure: measureModeWidth " + measureModeWidth);

            if (measureModeWidth != MeasureSpec.UNSPECIFIED) {
                int maximumWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (movieWidth > maximumWidth) {
                    scaleH = (float) movieWidth / (float) maximumWidth;
                }
            }

			/*
             * calculate vertical scaling
			 */
            float scaleW = 1f;
            int measureModeHeight = MeasureSpec.getMode(heightMeasureSpec);
            Log.d(TAG, "onMeasure: measureModeHeight " + measureModeHeight);
            if (measureModeHeight != MeasureSpec.UNSPECIFIED) {
                int maximumHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (movieHeight > maximumHeight) {
                    scaleW = (float) movieHeight / (float) maximumHeight;
                }
            }

			/*
             * calculate overall scale
			 */
            mScale = 1f / Math.max(scaleH, scaleW);
            Log.d(TAG, "onMeasure: mScale " + mScale);

            mMeasuredMovieWidth = (int) (movieWidth * mScale);
            mMeasuredMovieHeight = (int) (movieHeight * mScale);
            Log.d(TAG, "onMeasure: finally mMeasuredMovieWidth "+ mMeasuredMovieWidth + " mMeasuredMovieHeight " + measureModeHeight);
            setMeasuredDimension(mMeasuredMovieWidth, mMeasuredMovieHeight);

        } else {
			/*
			 * No movie set, just set minimum available size.
			 */
            setMeasuredDimension(getSuggestedMinimumWidth(), getSuggestedMinimumHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        //在使用layoutParams设置宽高时, Margin值也会作为MAX宽高的一部分生效, 所以请设置位置的时候不用使用Margin值, 尽量用setX()这种
        if(minWidth == 0){
            //算出宽高比
            whRatio = getWidth()*1f/getHeight();
//            whRatio = mScale;
            //我设置的最小宽度是默认宽度的2分之1 这个可以随便设置
            minWidth = getWidth()/2;
            ViewGroup parent = (ViewGroup) getParent();
            maxWidth = parent.getWidth();//设置最大宽度是父view的宽度

            minHeight = getHeight()/2;
            maxHeight = (int) (maxWidth / whRatio);
        }
        Log.d(TAG, "onLayout: whRatio " + whRatio + minWidth + minHeight + maxWidth + maxHeight);
                /*
		 * Calculate mLeft / mTop for drawing in center
		 */
        mLeft = (getWidth() - mMeasuredMovieWidth) / 2f;
        mTop = (getHeight() - mMeasuredMovieHeight) / 2f;

        mVisible = getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (movie != null) {
            if (!mPaused) {
                updateAnimationTime();
                drawMovieFrame(canvas);
                invalidateView();
            } else {
                drawMovieFrame(canvas);
            }
        }
    }


    public void play() {
        if (this.mPaused) {
            this.mPaused = false;

            /**
             * Calculate new movie start time, so that it resumes from the same
             * frame.
             */
            mMovieStart = android.os.SystemClock.uptimeMillis() - mCurrentAnimationTime;
            Log.d(TAG, "play current time is" + mMovieStart );
            invalidate();
        }
    }

    public void pause() {
        if (!this.mPaused) {
            this.mPaused = true;

            invalidate();
        }

    }
    public boolean isPaused() {
        return this.mPaused;
    }

    public boolean isPlaying() {
        return !this.mPaused;
    }

    /**
     * Invalidates view only if it is mVisible.
     * <br>
     * {@link #postInvalidateOnAnimation()} is used for Jelly Bean and higher.
     */
    @SuppressLint("NewApi")
    private void invalidateView() {
        if (mVisible) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                postInvalidateOnAnimation();
            } else {
                invalidate();
            }
        }
    }

    private void updateAnimationTime() {
        long now = android.os.SystemClock.uptimeMillis();
        Log.d(TAG, "updateAnimationTime: now" + now);

        if (mMovieStart == 0) {
            mMovieStart = now;
        }

        int dur = movie.duration();
        if (dur == 0) {
            dur = DEFAULT_MOVIE_VIEW_DURATION;
            Log.d(TAG, "updateAnimationTime: dur " + dur);
        }
        mCurrentAnimationTime = (int) ((now - mMovieStart) % dur);
        Log.d(TAG, "updateAnimationTime: mCurrentAniMationTime "+ mCurrentAnimationTime);
        indexFrame = (float) mCurrentAnimationTime/dur;
    }

    /**
     * Draw current GIF frame
     */
    private void drawMovieFrame(Canvas canvas) {

        movie.setTime(mCurrentAnimationTime);

        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(mScale, mScale);
        movie.draw(canvas, mLeft, mTop);
        canvas.restore();
    }

    @SuppressLint("NewApi")
    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        mVisible = screenState == SCREEN_STATE_ON;
        invalidateView();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mVisible = visibility == View.VISIBLE;
        invalidateView();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == View.VISIBLE;
        invalidateView();
    }

    View copyView;
    float lastRota;

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                firstX = downX = event.getRawX();
                firstY = downY = event.getRawY();
                //得到view的中心点坐标
                coreX = getWidth()/2+getX();
                coreY = getHeight()/2+getY();
                Log.d(TAG, "onTouchEvent: firstX " + firstX + "firstY " + firstY  + "coreX " + coreX + "coreY " +coreY);
                break;
            case MotionEvent.ACTION_MOVE:
                //得到手指触摸点数量
                int pointerCount = event.getPointerCount();
                //双点触摸事件
                if(pointerCount >= 2){
                    doubleMove = true;//双指手势触摸状态
                    pause();
                    getGifFrame();
                    this.setBackground(mPausedDrawable);
                    float distance = getSlideDis(event);
                    float spaceRotation = getRotation(event);
                    //复制一个同样的copyView盖到原原view上
                    if(copyView == null){
                        copyView = new View(getContext());
                        copyView.setX(getX());
                        copyView.setY(getY());
                        copyView.setRotation(getRotation());
                        copyView.setBackground(getBackground());
                        copyView.setLayoutParams(new ViewGroup.LayoutParams(getWidth(), getHeight()));
                        Log.d(TAG, "onTouchEvent: copyView " + "X " + getX() + "Y " +getY() + " Rotation " +getRotation());
                        ViewGroup parent = (ViewGroup) getParent();
                        parent.addView(copyView);
//                        copyView.setAlpha(0);
//                        parent.removeView(this);
                        this.setVisibility(GONE);
                        //把原view隐藏
//                        setAlpha(1);
                    }else{
                        //放大缩小逻辑
//                        setAlpha(0);
//                        copyView.setAlpha(1);
                        int slide = (int) (lastDis - distance);
                        int slide2 = (int) (slide/whRatio);
                        Log.d(TAG, "onTouchEvent: slide slide2 " + slide + " " + slide2);
                        ViewGroup.LayoutParams layoutParams = getLayoutParams();
                        layoutParams.width = (layoutParams.width - slide);
                        layoutParams.height = layoutParams.height - slide2;

                        if(layoutParams.width > maxWidth || layoutParams.height > maxHeight){//判断长宽最大值
                            layoutParams.width = maxWidth;
                            layoutParams.height = maxHeight;
                        }else if(layoutParams.width < minWidth || layoutParams.height < minHeight){
                            layoutParams.width = minWidth;
                            layoutParams.height = minHeight;
                        }

                        //当这个view宽高发生变化时, 我们要此view的中心点还是保持不变, 所以要重新设置x和y轴的坐标
                        setLayoutParams(layoutParams);
                        float x = coreX - getWidth() / 2;
                        float y = coreY - getHeight() / 2;
                        Log.d(TAG, "onTouchEvent: 放大缩小后的 x y " + x + " " + y);
                        setX(x);
                        setY(y);
                        copyView.setX(x);
                        copyView.setY(y);

                        //将copyView的长宽变成和原view一样大小, 这是为了每次手势缩放时视觉保持一致
                        ViewGroup.LayoutParams layoutParams1 = copyView.getLayoutParams();
                        layoutParams1.width = layoutParams.width;
                        layoutParams1.height = layoutParams.height;
                        copyView.setLayoutParams(layoutParams1);

                        if(lastRota != 0){
                            float f = lastRota-spaceRotation;
                            //重点来了, 这时我们对view进行的旋转操作全部施加在copyView上了
                            //这样我们得到的旋转参数每次就正常了, 因为我们的手势是操控在原view上的
                            copyView.setRotation(copyView.getRotation()-f);
                        }
                    }
                    //记录本次双指旋转度数
                    lastRota = spaceRotation;
                    //记录本次双指相距距离
                    lastDis = distance;
                }else if(!doubleMove && pointerCount == 1){//单点移动事件
                    float moveX = event.getRawX();
                    float moveY = event.getRawY();
                    //根据上次坐标, 计算出本次手指移动距离
                    float slideX = moveX - downX;
                    float slideY = moveY - downY;
                    //设置view坐标
                    setX(getX()+slideX);
                    setY(getY()+slideY);
                    //记录view移动后的坐标值
                    downX = moveX;
                    downY = moveY;
                }
                break;
            case MotionEvent.ACTION_UP:
                //当手指抬起时, 加进copyView上的徐娜转参数赋值回去
                if(copyView != null){
//                    setAlpha(1);
                    this.setVisibility(VISIBLE);
                    setRotation(copyView.getRotation());
                    ViewGroup parent = (ViewGroup) getParent();
                    parent.removeView(copyView);
                }

                //初始化所有参数信息
                lastRota = 0;
                copyView = null;
                doubleMove = false;
                lastDis = 0;

                //因为重写了onTouch, 所以也要重写单击事件
                float upX = event.getRawX();
                float upY = event.getRawY();
                if (Math.abs(upX - firstX) < 10 && Math.abs(upY - firstY) < 10 && clickable) {
                    if (listener != null) listener.onClick(this);//触发单击
                }
                play();
                break;
        }
        return true;
    }

    /**
     * 因为重写的onTouch, 那么onClick也必须要重写
     */
    @Override
    public void setOnClickListener(OnClickListener listener) {
        this.listener = listener;
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }
    /**
     * 获取手指间的旋转角度
     */
    private float getRotation(MotionEvent event) {

        double deltaX = event.getX(0) - event.getX(1);
        double deltaY = event.getY(0) - event.getY(1);
        double radians = Math.atan2(deltaY, deltaX);
        return (float) Math.toDegrees(radians);
    }

    /**
     * 获取手指间的距离
     */
    private float getSlideDis(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }





    public void setGifResource(int movieResourceId) {
        this.mMovieResourceId = movieResourceId;
        movie = Movie.decodeStream(getResources().openRawResource(mMovieResourceId));
        requestLayout();
    }

    public int getGifResource() {
        return this.mMovieResourceId;
    }

    //得到特定时间的GIF帧并降之转化为drawable，以便于设置背景
    @SuppressWarnings("ResourceType")
    private void getGifFrame(){
        gifImageDecoder = new GifImageDecoder();
        try {
            gifImageDecoder.read(this.getResources().openRawResource(R.mipmap.beauty5));
            int frameCount = gifImageDecoder.getFrameCount();
            Bitmap pausedBitmap = gifImageDecoder.getFrame(Math.round(frameCount*indexFrame));
            mPausedDrawable = new BitmapDrawable(pausedBitmap);
        }catch (Resources.NotFoundException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
