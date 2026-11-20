package com.smartdriver.calc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Organiza as etiquetas em linhas, quebrando quando nao cabem. */
public class FlowLayout extends ViewGroup {
    private final int gap;

    public FlowLayout(Context c, AttributeSet a) {
        super(c, a);
        gap = (int) (8 * c.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int max = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
            if (x > 0 && x + v.getMeasuredWidth() > max) { x = 0; y += rowH + gap; rowH = 0; }
            x += v.getMeasuredWidth() + gap;
            rowH = Math.max(rowH, v.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + rowH + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > max) { x = 0; y += rowH + gap; rowH = 0; }
            v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
    }
}
