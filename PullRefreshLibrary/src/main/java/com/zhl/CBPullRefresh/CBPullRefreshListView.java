package com.zhl.CBPullRefresh;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;


public class CBPullRefreshListView extends ListView implements OnScrollListener {
	private float mLastY = -1; // save event y
	private Scroller mScroller; // used for scroll back
	private OnScrollListener mScrollListener; // user's scroll listener

	// the interface to trigger refresh and load more.
	private MyListViewListener mListViewListener;

	// -- header view
	private CBRefreshHeaderView mHeaderView;
	// header view content, use it to calculate the Header's height. And hide it
	// when disable pull refresh.
	private RelativeLayout mHeaderViewContent;
	private TextView mHeaderTimeView;
	private int mHeaderViewHeight; // header view's height
	private boolean mEnablePullRefresh = true;
	private boolean mPullRefreshing = false; // is refreashing.

	// -- footer view
	private CBRefreshFooter mFooterView;
	private boolean mEnablePullLoad;
	private boolean mPullLoading;
	private boolean mIsFooterReady = false;

	// total list items, used to detect is at the bottom of listview.
	private int mTotalItemCount;

	// for mScroller, scroll back from header or footer.
	private int mScrollBack;
	private final static int SCROLLBACK_HEADER = 0;
	private final static int SCROLLBACK_FOOTER = 1;

	private final static int SCROLL_DURATION = 200; // scroll back duration
	private final static int PULL_LOAD_MORE_DELTA = 120; // when pull up >= 120px
															// at bottom,
															// trigger
															// load more.
	private final static float OFFSET_RADIO = 2.3f; // support iOS like pull
													// feature.
	private boolean showTopSearchBar = false;
	private CBRefreshTopSearchView topSearchView;
	private ImageView topSearch;
	private int topSearchBarHeight=0;
	private OnSearchClickListener searchClickListener;
	/**
	 * @param context
	 */
	public CBPullRefreshListView(Context context) {
		super(context);
		initWithContext(context);
	}

	public CBPullRefreshListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initWithContext(context);
	}

	public CBPullRefreshListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initWithContext(context);
	}

	private void initWithContext(Context context) {
		mScroller = new Scroller(context, new DecelerateInterpolator());
		// cbrefresh need the scroll event, and it will dispatch the event to
		// user's listener (as a proxy).
		super.setOnScrollListener(this);
		// init header view
		mHeaderView = new CBRefreshHeader(context);
		mHeaderViewContent = (RelativeLayout) mHeaderView.findViewById(R.id.cbrefresh_header_content);
		mHeaderTimeView = (TextView) mHeaderView.findViewById(R.id.cbrefresh_header_time);
		addHeaderView(mHeaderView);
		
		topSearchView = new CBRefreshTopSearchView(context);
		topSearch = (ImageView) topSearchView.findViewById(R.id.pull2reresh_top_search);
//		topSearch.setBackgroundResource(MyApplication.getInstance().isNightStyle()?R.drawable.home_search_bar:R.drawable.home_search_bar);
		topSearch.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(searchClickListener!=null){
					searchClickListener.onSearchBarClick();
				}
			}
		});
		addHeaderView(topSearchView);
		topSearchView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				topSearchBarHeight = topSearch.getHeight();
				getViewTreeObserver().removeGlobalOnLayoutListener(this);
			}
		});
		
		// init footer view
		mFooterView = new CBRefreshFooter(context);

		// init header height
		mHeaderView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				mHeaderViewHeight = mHeaderViewContent.getHeight();
				getViewTreeObserver().removeGlobalOnLayoutListener(this);
			}
		});
		// headerview 和footer 与内容之间无分割线
		setHeaderDividersEnabled(false);
		
	}

	@Override
	public void setAdapter(ListAdapter adapter) {
		// make sure XListViewFooter is the last footer view, and only add once.
		if (mIsFooterReady == false) {
			mIsFooterReady = true;
			addFooterView(mFooterView);
			setFooterDividersEnabled(false);
		}
		setHeaderDividersEnabled(false);
		setRefreshTime(null);
		super.setAdapter(adapter);
	}

	/**
	 * enable or disable pull down refresh feature.
	 * 
	 * @param enable
	 */
	public void setPullRefreshEnable(boolean enable) {
		mEnablePullRefresh = enable;
		if (!mEnablePullRefresh) { // disable, hide the content
			mHeaderViewContent.setVisibility(View.INVISIBLE);
		} else {
			mHeaderViewContent.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * enable or disable pull up load more feature.
	 * 
	 * @param enable
	 */
	public void setPullLoadMoreEnable(boolean enable) {
		mEnablePullLoad = enable;
		if (!mEnablePullLoad) {
			mFooterView.hide();
			mFooterView.setOnClickListener(null);
		} else {
			mPullLoading = false;
			mFooterView.show();
			mFooterView.setState(CBRefreshFooter.STATE_NORMAL);
			// both "pull up" and "click" will invoke load more.
			mFooterView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startLoadMore();
				}
			});
		}
	}

	/**
	 * stop refresh, reset header view.
	 */
	public void stopRefresh() {
		if (mPullRefreshing == true) {
			mPullRefreshing = false;
			resetHeaderHeight();
			resetTopSearchBarHeight();
		}
	}
	
	public void startRefresh() {
		mPullRefreshing = true;
		((CBRefreshHeader) mHeaderView).setState(CBRefreshHeader.STATE_REFRESHING);
		mScrollBack = SCROLLBACK_HEADER;
		((CBRefreshHeader) mHeaderView).setVisiableHeight(mHeaderViewHeight);
		resetHeaderHeight();
	}

	/**
	 * stop load more, reset footer view.
	 */
	public void stopLoadMore() {
		if (mPullLoading == true) {
			mPullLoading = false;
			mFooterView.setState(CBRefreshFooter.STATE_NORMAL);
		}
	}

	/**
	 * set last refresh time
	 * 
	 * @param time
	 */
	@SuppressLint("SimpleDateFormat")
	public void setRefreshTime(String time) {
		if (null == time) {
			Date now = new Date();
			SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
			mHeaderTimeView.setText(dateFormat.format(now));
		} else {
			mHeaderTimeView.setText(time);
		}
	}

	private void invokeOnScrolling() {
		if (mScrollListener instanceof OnXScrollListener) {
			OnXScrollListener l = (OnXScrollListener) mScrollListener;
			l.onXScrolling(this);
		}
	}

	private void updateHeaderHeight(float delta) {
		((CBRefreshHeader) mHeaderView).setVisiableHeight((int) delta + ((CBRefreshHeader) mHeaderView).getVisiableHeight());
		if (mEnablePullRefresh && !mPullRefreshing) { // 未处于刷新状态，更新箭头
			if (((CBRefreshHeader) mHeaderView).getVisiableHeight() > mHeaderViewHeight) {
				((CBRefreshHeader) mHeaderView).setState(CBRefreshHeader.STATE_READY);
			} else {
				((CBRefreshHeader) mHeaderView).setState(CBRefreshHeader.STATE_NORMAL);
			}
		}
		setSelection(0); // scroll to top each time
	}

	/**
	 * reset header view's height.
	 */
	private void resetHeaderHeight() {
		int height = ((CBRefreshHeader) mHeaderView).getVisiableHeight();
		if (height == 0) // not visible.
			return;
		// refreshing and header isn't shown fully. do nothing.
		if (mPullRefreshing && height <= mHeaderViewHeight) {
			return;
		}
		int finalHeight = 0; // default: scroll back to dismiss header.
		// is refreshing, just scroll back to show all the header.
		if (mPullRefreshing && height > mHeaderViewHeight) {
			finalHeight = mHeaderViewHeight;
		}
		mScrollBack = SCROLLBACK_HEADER;
		mScroller.startScroll(0, height, 0, finalHeight - height, SCROLL_DURATION);
		// trigger computeScroll
		invalidate();
	}

	private void updateFooterHeight(float delta) {
		if(!mEnablePullLoad){
			return;
		}
		int height = mFooterView.getBottomMargin() + (int) delta;
		if (mEnablePullLoad && !mPullLoading) {
			if (height > PULL_LOAD_MORE_DELTA) { // height enough to invoke load more.
				mFooterView.setState(CBRefreshFooter.STATE_READY);
			} else {
				mFooterView.setState(CBRefreshFooter.STATE_NORMAL);
			}
		}
		mFooterView.setBottomMargin(height);

		// setSelection(mTotalItemCount - 1); // scroll to bottom
	}

	private void resetFooterHeight() {
		int bottomMargin = mFooterView.getBottomMargin();
		if (bottomMargin > 0) {
			mScrollBack = SCROLLBACK_FOOTER;
			mScroller.startScroll(0, bottomMargin, 0, -bottomMargin, SCROLL_DURATION);
			invalidate();
		}
	}

	private void startLoadMore() {
		mPullLoading = true;
		mFooterView.setState(CBRefreshFooter.STATE_LOADING);
		if (mListViewListener != null) {
			mListViewListener.onLoadMore();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (mLastY == -1) {
			mLastY = ev.getRawY();
		}

		switch (ev.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mLastY = ev.getRawY();
			break;
		case MotionEvent.ACTION_MOVE:
			final float deltaY = ev.getRawY() - mLastY;
			mLastY = ev.getRawY();
			if (getFirstVisiblePosition() == 0 && (((CBRefreshHeader) mHeaderView).getVisiableHeight() > 0 || deltaY > 0)) {
				// the first item is showing, header has shown or pull down.
				if(showTopSearchBar&&topSearchView.getVisiableHeight()<topSearchBarHeight){
					updateTopSearchBarHeight(deltaY);
				}else{
					if(mListViewListener != null){
						mListViewListener.setUpdateTime();
					}
					updateHeaderHeight(deltaY / OFFSET_RADIO);
					invokeOnScrolling();
				}
				
			} else if (getLastVisiblePosition() == mTotalItemCount - 1 && (mFooterView.getBottomMargin() > 0 || deltaY < 0)) {
				// last item, already pulled up or want to pull up.
				updateFooterHeight(-deltaY / OFFSET_RADIO);
			}
			break;
		default:
			mLastY = -1; // reset
			if (getFirstVisiblePosition() == 0) {
				// invoke refresh
				if (mEnablePullRefresh && ((CBRefreshHeader) mHeaderView).getVisiableHeight() > mHeaderViewHeight) {
					mPullRefreshing = true;
					((CBRefreshHeader) mHeaderView).setState(CBRefreshHeader.STATE_REFRESHING);
					if (mListViewListener != null) {
						mListViewListener.onRefresh();
					}
				}
				resetHeaderHeight();
			} else if (getLastVisiblePosition() == mTotalItemCount - 1) {
				// invoke load more.
				if (mEnablePullLoad && mFooterView.getBottomMargin() > PULL_LOAD_MORE_DELTA) {
					startLoadMore();
				}
				resetFooterHeight();
			}else{
				resetTopSearchBarHeight();
			}
			break;
		}
		return super.onTouchEvent(ev);
	}

	private void resetTopSearchBarHeight() {
		if(showTopSearchBar && topSearchView != null){
			topSearchView.setHeight(0);
		}
	}
	
	public void setTopSearchBarHeight() {
		if(topSearchView != null){
			topSearchView.setHeight(0);
		}
	}

	private void updateTopSearchBarHeight(float deltaY) {
		if(deltaY>20){
			topSearchView.setHeight(topSearchBarHeight);
		}
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			if (mScrollBack == SCROLLBACK_HEADER) {
				((CBRefreshHeader) mHeaderView).setVisiableHeight(mScroller.getCurrY());
			} else {
				mFooterView.setBottomMargin(mScroller.getCurrY());
			}
			postInvalidate();
			invokeOnScrolling();
		}
		super.computeScroll();
	}

	@Override
	public void setOnScrollListener(OnScrollListener l) {
		mScrollListener = l;
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (mScrollListener != null) {
			mScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		// send to user's listener
		mTotalItemCount = totalItemCount;
		if (mScrollListener != null) {
			mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	
	/**
	 * 设置顶部刷新图标
	 * @param resName
	 */
	public void setHeaderRefreshIcon(int resName){
		if(mHeaderView!=null){
			((CBRefreshHeader) mHeaderView).setHeaderRefreshIcon(resName);
		}
	}
	
	/**
	 * 设置底部加载更多背景
	 * @param resName
	 */
	public void setFooterBg(int resName){
		if(mFooterView!=null){
			mFooterView.setFooterBg(resName);
		}
	}
	/**
	 * 是否显示顶部搜索栏
	 * @param show
	 */
	public void showTobSearchBar(boolean show){
		this.showTopSearchBar = show;
	}
	
	public void showHeaderAnim(boolean show){
		if(show){
			mHeaderView.onAnimStart();
		}else{
			mHeaderView.onAnimCancel();
		}
	}
	
	public void setHeaderAnimTextColor(int color){
		mHeaderView.setHeaderAnimTextColor(color);
	}
	
	public void setMyListViewListener(MyListViewListener l) {
		mListViewListener = l;
	}
	
	public void setOnItemClickListener(final OnItemClickListener listener) {
		this.setOnItemClickListener(new AbsListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if(listener!=null){
					//因为这里加了两个头　内容从position-2开始
					listener.onItemClick(parent, view, position-2, id);
				}
			}
		});
	}
	
	public void setOnSearchBarClickListener(OnSearchClickListener searchClickListener){
		this.searchClickListener = searchClickListener;
	}

	/**
	 * you can listen ListView.OnScrollListener or this one. it will invoke
	 * onXScrolling when header/footer scroll back.
	 */
	public interface OnXScrollListener extends OnScrollListener {
		public void onXScrolling(View view);
	}

	public void setTopSearchDrawable(int resid){
		if(topSearch!=null){
			topSearch.setBackgroundResource(resid);
		}
	}
	/**
	 * implements this interface to get refresh/load more event.
	 */
	public interface MyListViewListener {
		public void onRefresh();
		public void onLoadMore();
		public void setUpdateTime();
	}
	
	public interface OnItemClickListener{
		public void onItemClick(AdapterView<?> parent, View view,
								int position, long id);
	}
	public interface OnSearchClickListener{
		public void onSearchBarClick();
	}
	public interface OnHeaderAnimationListenr{
		public void onAnimStart();
		public void onAnimCancel();
	}
	
}
