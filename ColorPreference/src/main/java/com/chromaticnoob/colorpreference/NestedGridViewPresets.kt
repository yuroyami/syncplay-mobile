package com.chromaticnoob.colorpreference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridView;

public class NestedGridViewPresets extends GridView {

  public NestedGridViewPresets(Context context) {
    super(context);
  }

  public NestedGridViewPresets(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public NestedGridViewPresets(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
    super.onMeasure(widthMeasureSpec, expandSpec);
  }
}
