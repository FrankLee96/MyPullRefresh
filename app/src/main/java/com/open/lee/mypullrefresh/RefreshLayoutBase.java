package com.open.lee.mypullrefresh;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.ContactsContract;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import com.open.lee.mypullrefresh.Listener.OnRefreshListener;

/**
 * Created by Lee on 2016/11/24.
 */

public abstract class RefreshLayoutBase<T extends View> extends ViewGroup {

    //Scroller
    private Scroller mScroller;

    //下拉显示的头部视图
    private View mHeaderView;

    //初始上滑距离
    private int mInitScrollY;

    //内容视图，用户自行设置
    protected T mContentView;

    //上次触摸事件Y坐标
    private int mLastY;

    //下拉操作的每次滑动偏移量
    private int mYOffset;

    //提示的文本
    private TextView tipTextView;

    //箭头ImageView
    private ImageView arrowImageView;

    //等待ImageView（有动画）
    private ImageView waitImageView;

    //刷新成功之后显示的ImageView
    private ImageView successImageView;

    //刷新失败之后显示的ImageView
    private ImageView failureImageView;

    /**
     * 刷新回调Listener和set函数
     */
    private OnRefreshListener mRefreshListener;

    public void setOnRefreshListener(OnRefreshListener listener){
        this.mRefreshListener = listener;
    }

    /**
     * 刷新状态枚举：刷新中、初始状态、下拉刷新（已拉动）、释放刷新（已拉动）
     */
    private enum RefreshState {
        REFRESHING_STATE,
        IDLE_STATE,
        PULL_TO_REFRESH,
        RELEASE_TO_REFRESH
    }

    //刷新状态,初始为初始状态
    private RefreshState mState = RefreshState.IDLE_STATE;

    public RefreshLayoutBase(Context context){
        this(context, null);
    }

    public RefreshLayoutBase(Context context, AttributeSet attrs){
        this(context, attrs, 0);
    }

    public RefreshLayoutBase(Context context, AttributeSet attrs, int defStyle){
        super(context, attrs);
        mScroller = new Scroller(context);

        //设置内容视图
        setContentView(context);
        //设置头部视图
        setHeaderView(context);
        //添加用户设置的内容视图
        addView(mContentView);
    }

    /**
     * 初始化头部视图，
     * @param context context
     */
    protected void setHeaderView(Context context){
        mHeaderView = LayoutInflater.from(context).inflate(R.layout.pull_to_refresh_header, this, false);
        addView(mHeaderView);

        //find the widget of headerView
        tipTextView = (TextView) mHeaderView.findViewById(R.id.pull_to_refresh_text);
        arrowImageView = (ImageView) mHeaderView.findViewById(R.id.refresh_arrow_image);
        waitImageView = (ImageView) mHeaderView.findViewById(R.id.wait_circuit_image);
        successImageView = (ImageView) mHeaderView.findViewById(R.id.refresh_success_image);
        failureImageView = (ImageView) mHeaderView.findViewById(R.id.refresh_failure_image);
    }

    protected abstract void setContentView(Context context);

    /**
     * 判断ContentView是否位于顶部，留给子类实现
     */
    protected abstract boolean isTop();

    /**
     * 测量工作，宽度为用户设置的宽度，高度为HeaderView和ContentView高度之和
     * 也可以考虑实现用户设置的高度
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        Log.d("test", "Width :" + width);

        int childCount = getChildCount();

        int finalHeight = 0;
        for (int i = 0; i < childCount; i++){
            View child = getChildAt(i);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            finalHeight += child.getMeasuredHeight();
        }

        setMeasuredDimension(width, finalHeight);
    }

    /**
     * 布局工作，从上到下布局
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int childCount = getChildCount();
        Log.d("test", "ChildCount is:" + childCount);
        for (int i = 0; i < childCount; i++){
            View child = getChildAt(i);
            child.layout(left, top, child.getMeasuredWidth(), child.getMeasuredHeight() + top);
            top += child.getMeasuredHeight();
            Log.d("test", "Child" + i + "height: " + child.getMeasuredHeight());
        }
        mInitScrollY = mHeaderView.getMeasuredHeight() + getPaddingTop();
        scrollTo(0, mInitScrollY);
    }

    /**
     * 在下拉操作，并且ContentView位于顶端时拦截触摸事件
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        if(action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP){
            return false;
        }

        switch (action){
            case MotionEvent.ACTION_DOWN:
                mLastY = (int) ev.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                if(isTop() && ev.getRawY() - mLastY > 0){
                    return true;
                }
                break;
        }

        //其余情况都不会拦截
        return false;
    }

    /**
     * 处理符合条件的触摸事件，进行刷新逻辑和控件状态改变逻辑
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mState == RefreshState.REFRESHING_STATE)
            return true;
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                mLastY = (int) event.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                int currentY = (int) event.getRawY();
                mYOffset = currentY - mLastY;
                changeScrollY(mYOffset);
                mLastY = currentY;
                break;

            case MotionEvent.ACTION_UP:
                int curScrollY = getScrollY();
                if(curScrollY < mInitScrollY / 4){
                    refresh();
                } else {
                    recoverToInitState();
                }
                break;

            /**
             * 监听取消事件，防止下拉过程中锁屏之后不复原的BUG，在锁屏时恢复初始状态
             */
            case MotionEvent.ACTION_CANCEL:
                recoverToInitState();
                break;
        }

        return true;
    }

    /**
     * 根据每两次MOVE之间的Y轴坐标差值，在Y轴上进行控件的移动，移动的距离就是差值
     * @param distance 移动距离
     */
    private void changeScrollY(int distance){
        int curY = getScrollY();

        Log.d("test", "Height is:" + mHeaderView.getHeight());
        if (distance > 0 && curY - distance > getPaddingTop()) {
            // 下拉过程
            scrollBy(0, -distance);
        } else if (distance < 0 && curY - distance <= mInitScrollY) {
            // 上滑过程
            scrollBy(0, -distance);
        }

        int slop = mInitScrollY / 4;
        if(curY > 0 && curY < slop){
            mState = RefreshState.RELEASE_TO_REFRESH;
        } else if (curY > 0 && curY > slop){
            mState = RefreshState.PULL_TO_REFRESH;
        }
        changeWidgetState();
    }

    /**
     * 根据当前状态设置HeaderView的子控件
     */
    private void changeWidgetState(){
        switch (mState){
            case PULL_TO_REFRESH:
                tipTextView.setText("下拉刷新");
                arrowImageView.setRotation(0);
                break;

            case RELEASE_TO_REFRESH:
                tipTextView.setText("释放立即刷新");
                arrowImageView.setRotation(180);
                break;


            case REFRESHING_STATE:
                arrowImageView.setVisibility(INVISIBLE);
                waitImageView.setVisibility(VISIBLE);
                startWaitAnimation();
                tipTextView.setText("正在刷新...");
                break;
        }
    }

    /**
     * 刷新操作
     */
    private void refresh(){
        mState = RefreshState.REFRESHING_STATE;
        mScroller.startScroll(getScrollX(), getScrollY(),
                0, mInitScrollY / 2 - getScrollY());
        invalidate();
        changeWidgetState();
        if(mRefreshListener != null && mState == RefreshState.REFRESHING_STATE){
            mRefreshListener.onRefresh();
        }
    }

    private void startWaitAnimation(){
        if (mState != RefreshState.REFRESHING_STATE)
            return;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(700);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float currentValue = (Float)valueAnimator.getAnimatedValue();
                waitImageView.setRotation(currentValue);
            }
        });
        animator.setRepeatCount(100);
        animator.start();
    }

    private void recoverToInitState(){
        Log.d("test", "Scroll is:" + getScrollY() + "");
        mScroller.startScroll(getScrollX(), getScrollY(),
                0, mInitScrollY - getScrollY());
        this.invalidate();
        //successImageView.setVisibility(INVISIBLE);
        this.postDelayed(new Runnable() {
            @Override
            public void run() {
                arrowImageView.setVisibility(VISIBLE);
                waitImageView.setVisibility(INVISIBLE);
                successImageView.setVisibility(INVISIBLE);
                failureImageView.setVisibility(INVISIBLE);
            }
        }, 100);
    }

    @Override
    public void computeScroll() {
        if(mScroller.computeScrollOffset()){
            this.scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            this.postInvalidate();
        }
    }

    public void completeRefresh(){
        tipTextView.setText("刷新成功");
        waitImageView.setVisibility(INVISIBLE);
        successImageView.setVisibility(VISIBLE);


        /**
         * A problem here to be solved!
         * 当调用设置为VISIBLE的时候，其自动Scroll到了最上面的位置???理由不清楚
         */
        mScroller.startScroll(getScrollX(), getScrollY(),
                0, mInitScrollY / 2 - getScrollY());
        invalidate();

        mState = RefreshState.IDLE_STATE;
        this.postDelayed(new Runnable() {
            @Override
            public void run() {
                recoverToInitState();
            }
        }, 400);
    }

    public void failRefresh(){
        tipTextView.setText("刷新失败");
        waitImageView.setVisibility(INVISIBLE);
        failureImageView.setVisibility(VISIBLE);


        /**
         * A problem here to be solved!
         * 当调用设置为VISIBLE的时候，其自动Scroll到了最上面的位置???理由不清楚
         */
        mScroller.startScroll(getScrollX(), getScrollY(),
                0, mInitScrollY / 2 - getScrollY());
        invalidate();


        mState = RefreshState.IDLE_STATE;
        this.postDelayed(new Runnable() {
            @Override
            public void run() {
                recoverToInitState();
            }
        }, 400);
    }
}
