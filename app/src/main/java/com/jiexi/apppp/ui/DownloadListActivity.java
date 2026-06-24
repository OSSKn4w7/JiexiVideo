package com.jiexi.apppp.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jiexi.apppp.R;
import com.jiexi.apppp.download.DownloadItem;
import com.jiexi.apppp.download.DownloadService;
import com.jiexi.apppp.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

public class DownloadListActivity extends Activity {

    private int mPlatform = -1; // -1 = show all, otherwise filter by platform
    private ListView mListView;
    private LinearLayout mEmptyLayout;
    private DownloadAdapter mAdapter;
    private DownloadService mDownloadService;
    private boolean mServiceBound;
    private Handler mHandler;
    private Runnable mRefreshTask;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
            mDownloadService = binder.getService();
            mServiceBound = true;
            refreshList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mDownloadService = null;
            mServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);

        mPlatform = getIntent().getIntExtra("platform", -1);

        mListView = (ListView) findViewById(R.id.downloadListView);
        mEmptyLayout = (LinearLayout) findViewById(R.id.emptyLayout);
        mHandler = new Handler();

        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button btnClearDone = (Button) findViewById(R.id.btnClearDone);
        btnClearDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCompleted();
            }
        });

        // Bind to download service
        Intent serviceIntent = new Intent(this, DownloadService.class);
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        mAdapter = new DownloadAdapter();
        mListView.setAdapter(mAdapter);

        // Auto refresh
        mRefreshTask = new Runnable() {
            @Override
            public void run() {
                refreshList();
                mHandler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mRefreshTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefreshTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

    private void refreshList() {
        if (mDownloadService == null) return;

        List<DownloadItem> all = mDownloadService.getAllTasks();
        List<DownloadItem> tasks;
        if (mPlatform >= 0) {
            tasks = new ArrayList<DownloadItem>();
            for (DownloadItem item : all) {
                if (item.platform == mPlatform) {
                    tasks.add(item);
                }
            }
        } else {
            tasks = all;
        }
        mAdapter.setData(tasks);

        if (tasks.isEmpty()) {
            mEmptyLayout.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        } else {
            mEmptyLayout.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        }
    }

    private void clearCompleted() {
        if (mDownloadService == null) return;

        List<DownloadItem> tasks = mDownloadService.getAllTasks();
        for (DownloadItem item : tasks) {
            if (item.status == DownloadItem.STATUS_COMPLETED
                    || item.status == DownloadItem.STATUS_FAILED) {
                mDownloadService.deleteTask(item.id);
            }
        }
        refreshList();
    }

    private class DownloadAdapter extends BaseAdapter {

        private List<DownloadItem> mData = new ArrayList<DownloadItem>();

        public void setData(List<DownloadItem> data) {
            mData = data;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public DownloadItem getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mData.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_download, null);
                holder = new ViewHolder();
                holder.titleText = (TextView) convertView.findViewById(R.id.downloadTitleText);
                holder.pathText = (TextView) convertView.findViewById(R.id.downloadPathText);
                holder.statusText = (TextView) convertView.findViewById(R.id.downloadStatusText);
                holder.progress = (ProgressBar) convertView.findViewById(R.id.downloadProgress);
                holder.actionBtn = (Button) convertView.findViewById(R.id.downloadActionBtn);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final DownloadItem item = getItem(position);

            holder.titleText.setText(item.title + " (" + item.qualityName + ")");
            holder.pathText.setText(item.filePath);

            switch (item.status) {
                case DownloadItem.STATUS_DOWNLOADING:
                    holder.statusText.setText("下载中 " + item.progress + "%  "
                            + FileUtil.formatSize(item.downloadedSize));
                    holder.progress.setVisibility(View.VISIBLE);
                    holder.progress.setProgress(item.progress);
                    holder.actionBtn.setText("暂停");
                    holder.actionBtn.setVisibility(View.VISIBLE);
                    holder.actionBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mDownloadService != null) {
                                mDownloadService.pauseTask(item.id);
                            }
                        }
                    });
                    break;

                case DownloadItem.STATUS_PAUSED:
                    holder.statusText.setText("已暂停 " + item.progress + "%  "
                            + FileUtil.formatSize(item.downloadedSize));
                    holder.progress.setVisibility(View.VISIBLE);
                    holder.progress.setProgress(item.progress);
                    holder.actionBtn.setText("继续");
                    holder.actionBtn.setVisibility(View.VISIBLE);
                    holder.actionBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mDownloadService != null) {
                                mDownloadService.resumeTask(item.id);
                            }
                        }
                    });
                    break;

                case DownloadItem.STATUS_COMPLETED:
                    holder.statusText.setText("已完成  " + FileUtil.formatSize(item.totalSize));
                    holder.progress.setVisibility(View.GONE);
                    holder.actionBtn.setText("删除");
                    holder.actionBtn.setVisibility(View.VISIBLE);
                    holder.actionBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mDownloadService != null) {
                                mDownloadService.deleteTask(item.id);
                                refreshList();
                            }
                        }
                    });
                    break;

                case DownloadItem.STATUS_FAILED:
                    holder.statusText.setText("下载失败");
                    holder.progress.setVisibility(View.GONE);
                    holder.actionBtn.setText("删除");
                    holder.actionBtn.setVisibility(View.VISIBLE);
                    holder.actionBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mDownloadService != null) {
                                mDownloadService.deleteTask(item.id);
                                refreshList();
                            }
                        }
                    });
                    break;

                default:
                    holder.statusText.setText("等待中");
                    holder.progress.setVisibility(View.GONE);
                    holder.actionBtn.setVisibility(View.GONE);
                    break;
            }

            return convertView;
        }
    }

    static class ViewHolder {
        TextView titleText;
        TextView pathText;
        TextView statusText;
        ProgressBar progress;
        Button actionBtn;
    }
}
