package me.jingbin.library.decoration;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.ArrayList;

import me.jingbin.library.ByRecyclerView;

/**
 * @author jingbin
 * 两步即可实现：
 * - 1.根布局使用：android:id="@id/id_by_sticky_item"
 * - 2.分类viewType 设置为：StickyView.TYPE_STICKY_VIEW
 * 注意：
 * - 只支持 LinearLayoutManager
 * - 第一个item必须为 StickyItem，所以不能使用自带下拉刷新
 * <p>
 * 原作者：https://github.com/chenpengfei88/StickyItemDecoration
 */
public class StickyItemDecoration extends RecyclerView.ItemDecoration {

    /**
     * 吸附的itemView
     */
    private View mStickyItemView;

    /**
     * 吸附itemView 距离顶部
     */
    private int mStickyItemViewMarginTop;

    /**
     * 吸附itemView 高度
     */
    private int mStickyItemViewHeight;

    /**
     * 通过它获取到需要吸附view的相关信息
     */
    private StickyView mStickyView;

    /**
     * adapter
     */
    private RecyclerView.Adapter<RecyclerView.ViewHolder> mAdapter;

    /**
     * viewHolder
     */
    private RecyclerView.ViewHolder mViewHolder;

    /**
     * position list
     */
    private ArrayList<Integer> mStickyPositionList = new ArrayList<>();

    /**
     * layout manager  LinearLayoutManager
     */
    private RecyclerView.LayoutManager mLayoutManager;

    /**
     * GridLayoutManager  spanSizeLookup
     */
    private GridLayoutManager.SpanSizeLookup spanSizeLookup = null;

    /**
     * 绑定数据的position
     */
    private int mBindDataPosition = -1;

    private ByRecyclerView byRecyclerView;

    public StickyItemDecoration() {
        mStickyView = new StickyViewImpl();
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDrawOver(c, parent, state);

        if (byRecyclerView == null) {
            if (parent instanceof ByRecyclerView) {
                byRecyclerView = (ByRecyclerView) parent;
            }
        }

        if (parent.getAdapter() == null || parent.getAdapter().getItemCount() <= getCustomTopPosition() + getCustomBottomPosition()) {
            return;
        }

        mLayoutManager = parent.getLayoutManager();

        if (spanSizeLookup == null && mLayoutManager instanceof GridLayoutManager) {
            GridLayoutManager manager = (GridLayoutManager) mLayoutManager;
            spanSizeLookup = manager.getSpanSizeLookup();
        }


        /**
         * 滚动过程中当前的UI是否可以找到吸附的view
         */
        boolean mCurrentUIFindStickView = false;

        for (int m = 0, size = parent.getChildCount(); m < size; m++) {
            View view = parent.getChildAt(m);

            /**
             * 如果是吸附的view
             */
            boolean spanSizeDOne = spanSizeLookup != null && spanSizeLookup.getSpanSize(getFindFirstVisibleItemPosition()) == 1;
            if (spanSizeDOne || mStickyView.isStickyView(view)) {
                mCurrentUIFindStickView = true;
                getStickyViewHolder(parent);
                cacheStickyViewPosition(m);

                if (view.getTop() <= 0) {
                    bindDataForStickyView(getFindFirstVisibleItemPosition(), parent.getMeasuredWidth());
                } else {
                    if (mStickyPositionList.size() > 0) {
                        if (mStickyPositionList.size() == 1) {
                            bindDataForStickyView(mStickyPositionList.get(0), parent.getMeasuredWidth());
                        } else {
                            int currentPosition = getStickyViewPositionOfRecyclerView(m);
                            int indexOfCurrentPosition = mStickyPositionList.lastIndexOf(currentPosition);
                            if (indexOfCurrentPosition >= 1) {
                                bindDataForStickyView(mStickyPositionList.get(indexOfCurrentPosition - 1), parent.getMeasuredWidth());
                            }
                        }
                    }
                }

                if (view.getTop() > 0 && view.getTop() <= mStickyItemViewHeight) {
                    mStickyItemViewMarginTop = mStickyItemViewHeight - view.getTop();
                } else {
                    mStickyItemViewMarginTop = 0;

                    View nextStickyView = getNextStickyView(parent);
                    if (nextStickyView != null && nextStickyView.getTop() <= mStickyItemViewHeight) {
                        mStickyItemViewMarginTop = mStickyItemViewHeight - nextStickyView.getTop();
                    }

                }

                drawStickyItemView(c);
                break;
            }
        }

        if (!mCurrentUIFindStickView) {
            mStickyItemViewMarginTop = 0;
            if (getFindFirstVisibleItemPosition() + parent.getChildCount() ==
                    parent.getAdapter().getItemCount() + getCustomTopPosition() + getCustomBottomPosition()
                    && mStickyPositionList.size() > 0) {
                bindDataForStickyView(mStickyPositionList.get(mStickyPositionList.size() - 1), parent.getMeasuredWidth());
            }
            drawStickyItemView(c);
        }
    }

    /**
     * 得到下一个吸附View
     */
    private View getNextStickyView(RecyclerView parent) {
        int num = 0;
        View nextStickyView = null;
        for (int m = 0, size = parent.getChildCount(); m < size; m++) {
            View view = parent.getChildAt(m);
            if (mStickyView.isStickyView(view)) {
                nextStickyView = view;
                num++;
            }
            if (num == 2) {
                break;
            }
        }
        return num >= 2 ? nextStickyView : null;
    }

    /**
     * 给StickyView绑定数据
     */
    private void bindDataForStickyView(int position, int width) {
        if (mBindDataPosition == position || mViewHolder == null) {
            return;
        }

        mBindDataPosition = position;
        mAdapter.onBindViewHolder(mViewHolder, mBindDataPosition);
        measureLayoutStickyItemView(width);
        mStickyItemViewHeight = mViewHolder.itemView.getBottom() - mViewHolder.itemView.getTop();
    }

    /**
     * 缓存吸附的view position
     */
    private void cacheStickyViewPosition(int m) {
        int position = getStickyViewPositionOfRecyclerView(m);
        if (!mStickyPositionList.contains(position)) {
            mStickyPositionList.add(position);
        }
    }

    /**
     * 得到吸附view在RecyclerView中 的position
     */
    private int getStickyViewPositionOfRecyclerView(int m) {
        return getFindFirstVisibleItemPosition() + m;
    }

    /**
     * 得到吸附viewHolder
     */
    private void getStickyViewHolder(RecyclerView recyclerView) {
        if (mAdapter != null) {
            return;
        }

        mAdapter = recyclerView.getAdapter();
        mViewHolder = mAdapter.onCreateViewHolder(recyclerView, mStickyView.getStickViewType());
        mStickyItemView = mViewHolder.itemView;
    }

    /**
     * 计算布局吸附的itemView
     */
    private void measureLayoutStickyItemView(int parentWidth) {
        if (mStickyItemView == null || !mStickyItemView.isLayoutRequested()) {
            return;
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY);
        int heightSpec;

        ViewGroup.LayoutParams layoutParams = mStickyItemView.getLayoutParams();
        if (layoutParams != null && layoutParams.height > 0) {
            heightSpec = View.MeasureSpec.makeMeasureSpec(layoutParams.height, View.MeasureSpec.EXACTLY);
        } else {
            heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }

        mStickyItemView.measure(widthSpec, heightSpec);
        mStickyItemView.layout(0, 0, mStickyItemView.getMeasuredWidth(), mStickyItemView.getMeasuredHeight());
    }

    /**
     * 绘制吸附的itemView
     */
    private void drawStickyItemView(Canvas canvas) {
        if (mStickyItemView == null) {
            return;
        }

        int saveCount = canvas.save();
        canvas.translate(0, -mStickyItemViewMarginTop);
        mStickyItemView.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    /**
     * 获取第一个显示item的position
     * LinearLayoutManager / StaggeredGridLayoutManager
     */
    private int getFindFirstVisibleItemPosition() {
        if (mLayoutManager instanceof LinearLayoutManager) {
            final LinearLayoutManager llm = (LinearLayoutManager) mLayoutManager;
            return llm.findFirstVisibleItemPosition() + getCustomTopPosition();
        } else {
            StaggeredGridLayoutManager sglm = (StaggeredGridLayoutManager) mLayoutManager;
            int[] first = new int[sglm.getSpanCount()];
            sglm.findFirstVisibleItemPositions(first);
            return first[0] + getCustomTopPosition();
        }
    }

    private int getCustomTopPosition() {
        if (byRecyclerView != null) {
            return byRecyclerView.getCustomTopItemViewCount();
        }
        return 0;
    }

    private int getCustomBottomPosition() {
        if (byRecyclerView != null) {
            return byRecyclerView.getFooterViewSize() + byRecyclerView.getLoadMoreSize();
        }
        return 0;
    }
}
