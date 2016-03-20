package com.zhl.cbpullrefreshlistview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.zhl.CBPullRefresh.CBPullRefreshListView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private TestAdapter mAdatper;
    private ArrayList<String> DataList = new ArrayList<String>();
    private CBPullRefreshListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initData();
        initView();
    }

    private void initView() {
        mListView = (CBPullRefreshListView) findViewById(R.id.listview);
        mListView.setAdapter(mAdatper = new TestAdapter());
        mListView.setPullRefreshEnable(true);
        mListView.setPullLoadMoreEnable(true);
        mListView.setMyListViewListener(new CBPullRefreshListView.MyListViewListener() {
            @Override
            public void onRefresh() {
                mListView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mListView.stopRefresh();
                    }
                },5000);
            }

            @Override
            public void onLoadMore() {
                mListView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mListView.stopLoadMore();
                    }
                },5000);
            }

            @Override
            public void setUpdateTime() {

            }
        });
    }

    private void initData() {
        for (int i = 0; i < 30; i++) {
            DataList.add(new String("this is a cbpullrefreshlistview test" + i));
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    class TestAdapter extends BaseAdapter {


        @Override
        public int getCount() {
            return DataList == null ? 0 : DataList.size();
        }

        @Override
        public Object getItem(int position) {
            return DataList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder = null;
            if (convertView == null) {
                viewHolder = new ViewHolder();
                convertView = getLayoutInflater().inflate(R.layout.item_listview, parent, false);
                viewHolder.title = (TextView) convertView.findViewById(R.id.item_title);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            viewHolder.title.setText(DataList.get(position));
            return convertView;
        }
    }

    class ViewHolder {
        TextView title;
    }

}
