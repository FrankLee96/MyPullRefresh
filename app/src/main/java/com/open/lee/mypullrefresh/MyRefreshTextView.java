package com.open.lee.mypullrefresh;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by Lee on 2016/11/24.
 */

public class MyRefreshTextView extends RefreshLayoutBase<TextView>{

    public MyRefreshTextView(Context context){
        super(context);
    }

    public MyRefreshTextView(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    @Override
    protected void setContentView(Context context) {
        mContentView = new TextView(context);
        ((TextView) mContentView).setText("TextView");
        mContentView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected boolean isTop() {
        return true;
    }
}
