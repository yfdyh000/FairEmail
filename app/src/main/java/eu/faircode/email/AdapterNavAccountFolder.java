package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2021 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AdapterNavAccountFolder extends RecyclerView.Adapter<AdapterNavAccountFolder.ViewHolder> {
    private Context context;
    private LifecycleOwner owner;
    private LayoutInflater inflater;

    private boolean nav_count;
    private int dp6;
    private int dp12;
    private int colorUnread;
    private int textColorSecondary;
    private int colorWarning;

    private boolean expanded = true;
    private boolean folders = true;
    private List<TupleAccountFolder> all = new ArrayList<>();
    private List<TupleAccountFolder> items = new ArrayList<>();

    private NumberFormat NF = NumberFormat.getNumberInstance();
    private DateFormat TF;

    private static final int QUOTA_WARNING = 95; // percent

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private View view;
        private ImageView ivItem;
        private ImageView ivBadge;
        private TextView tvItem;
        private TextView tvItemExtra;
        private ImageView ivExtra;
        private ImageView ivWarning;

        ViewHolder(View itemView) {
            super(itemView);

            view = itemView.findViewById(R.id.clItem);
            ivItem = itemView.findViewById(R.id.ivItem);
            ivBadge = itemView.findViewById(R.id.ivBadge);
            tvItem = itemView.findViewById(R.id.tvItem);
            tvItemExtra = itemView.findViewById(R.id.tvItemExtra);
            ivExtra = itemView.findViewById(R.id.ivExtra);
            ivWarning = itemView.findViewById(R.id.ivWarning);
        }

        private void wire() {
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            ivWarning.setOnClickListener(this);
        }

        private void unwire() {
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            ivWarning.setOnClickListener(null);
        }

        private void bindTo(TupleAccountFolder account) {
            int start = (account.folderName == null ? 0 : (expanded ? dp12 : dp6));
            view.setPaddingRelative(start, 0, 0, 0);

            if (account.folderName == null) {
                if ("connected".equals(account.state))
                    ivItem.setImageResource(account.primary
                            ? R.drawable.twotone_folder_special_24
                            : R.drawable.twotone_folder_done_24);
                else
                    ivItem.setImageResource(account.backoff_until == null
                            ? R.drawable.twotone_folder_24
                            : R.drawable.twotone_update_24);
            } else {
                if ("syncing".equals(account.folderSyncState))
                    ivItem.setImageResource(R.drawable.twotone_compare_arrows_24);
                else if ("downloading".equals(account.folderSyncState))
                    ivItem.setImageResource(R.drawable.twotone_cloud_download_24);
                else if (account.executing > 0)
                    ivItem.setImageResource(R.drawable.twotone_dns_24);
                else
                    ivItem.setImageResource("connected".equals(account.folderState)
                            ? R.drawable.twotone_folder_done_24
                            : R.drawable.twotone_folder_24);
            }

            int count;
            if (EntityFolder.DRAFTS.equals(account.folderType))
                count = account.messages;
            else
                count = account.unseen;

            Integer color = (account.folderName == null ? account.color : account.folderColor);
            if (color == null || !ActivityBilling.isPro(context))
                ivItem.clearColorFilter();
            else
                ivItem.setColorFilter(color);
            ivBadge.setVisibility(count == 0 || expanded ? View.GONE : View.VISIBLE);

            String name = account.getName(context);
            if (count == 0)
                tvItem.setText(name);
            else
                tvItem.setText(context.getString(R.string.title_name_count, name, NF.format(count)));

            tvItem.setTextColor(count == 0 ? textColorSecondary : colorUnread);
            tvItem.setTypeface(count == 0 ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
            tvItem.setVisibility(expanded ? View.VISIBLE : View.GONE);

            if (account.folderName == null) {
                tvItemExtra.setText(account.last_connected == null ? null : TF.format(account.last_connected));
                tvItemExtra.setVisibility(account.last_connected != null && expanded ? View.VISIBLE : View.GONE);
            } else {
                tvItemExtra.setText(NF.format(account.messages));
                tvItemExtra.setVisibility(nav_count && expanded ? View.VISIBLE : View.GONE);
            }

            ivExtra.setVisibility(View.GONE);

            Integer percent = account.getQuotaPercentage();

            if (account.error != null && account.folderName == null) {
                ivWarning.setImageResource(R.drawable.twotone_warning_24);
                ivWarning.setVisibility(expanded ? View.VISIBLE : View.GONE);
                view.setBackgroundColor(expanded ? Color.TRANSPARENT : colorWarning);
            } else if (percent != null && percent > QUOTA_WARNING && account.folderName == null) {
                ivWarning.setImageResource(R.drawable.twotone_disc_full_24);
                ivWarning.setVisibility(expanded ? View.VISIBLE : View.GONE);
                view.setBackgroundColor(expanded ? Color.TRANSPARENT : colorWarning);
            } else {
                ivWarning.setVisibility(View.GONE);
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        @Override
        public void onClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;

            TupleAccountFolder account = items.get(pos);
            if (account == null)
                return;

            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);

            if (account.folderName != null)
                lbm.sendBroadcast(
                        new Intent(ActivityView.ACTION_VIEW_MESSAGES)
                                .putExtra("account", account.id)
                                .putExtra("folder", account.folderId)
                                .putExtra("type", account.folderType));
            else {
                if (v.getId() == R.id.ivWarning && account.error == null) {
                    ToastEx.makeText(context, R.string.title_legend_quota, Toast.LENGTH_LONG).show();
                    return;
                }

                lbm.sendBroadcast(
                        new Intent(ActivityView.ACTION_VIEW_FOLDERS)
                                .putExtra("id", account.id));
            }
        }

        @Override
        public boolean onLongClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return false;

            TupleAccountFolder account = items.get(pos);
            if (account == null || account.folderName != null)
                return false;

            Bundle args = new Bundle();
            args.putLong("id", account.id);

            new SimpleTask<EntityFolder>() {
                @Override
                protected EntityFolder onExecute(Context context, Bundle args) {
                    long id = args.getLong("id");

                    DB db = DB.getInstance(context);
                    return db.folder().getFolderByType(id, EntityFolder.INBOX);
                }

                @Override
                protected void onExecuted(Bundle args, EntityFolder inbox) {
                    if (inbox == null)
                        return;

                    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
                    lbm.sendBroadcast(
                            new Intent(ActivityView.ACTION_VIEW_MESSAGES)
                                    .putExtra("account", inbox.account)
                                    .putExtra("folder", inbox.id)
                                    .putExtra("type", inbox.type));
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    // Ignored
                }
            }.execute(context, owner, args, "account:inbox");

            return true;
        }
    }

    AdapterNavAccountFolder(Context context, LifecycleOwner owner) {
        this.context = context;
        this.owner = owner;
        this.inflater = LayoutInflater.from(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.nav_count = prefs.getBoolean("nav_count", false);
        boolean highlight_unread = prefs.getBoolean("highlight_unread", true);
        int colorHighlight = prefs.getInt("highlight_color", Helper.resolveColor(context, R.attr.colorUnreadHighlight));

        this.dp6 = Helper.dp2pixels(context, 6);
        this.dp12 = Helper.dp2pixels(context, 12);
        this.colorUnread = (highlight_unread ? colorHighlight : Helper.resolveColor(context, R.attr.colorUnread));
        this.textColorSecondary = Helper.resolveColor(context, android.R.attr.textColorSecondary);
        this.colorWarning = ColorUtils.setAlphaComponent(Helper.resolveColor(context, R.attr.colorWarning), 128);

        this.TF = Helper.getTimeInstance(context, SimpleDateFormat.SHORT);

        setHasStableIds(false);
    }

    public void set(@NonNull List<TupleAccountFolder> accounts, boolean expanded, boolean folders) {
        Log.i("Set nav accounts=" + accounts.size());

        if (accounts.size() > 0) {
            final Collator collator = Collator.getInstance(Locale.getDefault());
            collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

            Collections.sort(accounts, new Comparator<TupleAccountFolder>() {
                @Override
                public int compare(TupleAccountFolder a1, TupleAccountFolder a2) {
                    int a = Integer.compare(
                            a1.order == null ? -1 : a1.order,
                            a2.order == null ? -1 : a2.order);
                    if (a != 0)
                        return a;

                    int p = -Boolean.compare(a1.primary, a2.primary);
                    if (p != 0)
                        return p;

                    int n = collator.compare(a1.name, a2.name);
                    if (n != 0)
                        return n;

                    if (a1.folderName == null && a2.folderName == null)
                        return 0;
                    else if (a1.folderName == null)
                        return -1;
                    else if (a2.folderName == null)
                        return 1;

                    int o = Integer.compare(
                            a1.folderOrder == null ? -1 : a1.folderOrder,
                            a2.folderOrder == null ? -1 : a2.folderOrder);
                    if (o != 0)
                        return o;

                    int t1 = EntityFolder.FOLDER_SORT_ORDER.indexOf(a1.folderType);
                    int t2 = EntityFolder.FOLDER_SORT_ORDER.indexOf(a2.folderType);
                    int t = Integer.compare(t1, t2);
                    if (t != 0)
                        return t;

                    int s = -Boolean.compare(a1.folderSync, a2.folderSync);
                    if (s != 0)
                        return s;

                    return collator.compare(a1.getName(context), a2.getName(context));
                }
            });
        }

        all = accounts;
        if (!folders) {
            accounts = new ArrayList<>();
            for (TupleAccountFolder item : all)
                if (item.folderName == null)
                    accounts.add(item);
        }

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, accounts), false);

        this.expanded = expanded;
        this.items = accounts;

        diff.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                Log.d("Inserted @" + position + " #" + count);
            }

            @Override
            public void onRemoved(int position, int count) {
                Log.d("Removed @" + position + " #" + count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                Log.d("Moved " + fromPosition + ">" + toPosition);
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
                Log.d("Changed @" + position + " #" + count);
            }
        });
        diff.dispatchUpdatesTo(this);
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        notifyDataSetChanged();
    }

    public void setFolders(boolean folders) {
        if (this.folders != folders) {
            this.folders = folders;
            set(all, expanded, folders);
        }
    }

    public boolean hasFolders() {
        for (TupleAccountFolder item : all)
            if (item.folderName != null)
                return true;
        return false;
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private List<TupleAccountFolder> prev = new ArrayList<>();
        private List<TupleAccountFolder> next = new ArrayList<>();

        DiffCallback(List<TupleAccountFolder> prev, List<TupleAccountFolder> next) {
            this.prev.addAll(prev);
            this.next.addAll(next);
        }

        @Override
        public int getOldListSize() {
            return prev.size();
        }

        @Override
        public int getNewListSize() {
            return next.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            TupleAccountFolder a1 = prev.get(oldItemPosition);
            TupleAccountFolder a2 = next.get(newItemPosition);
            return a1.id.equals(a2.id) &&
                    Objects.equals(a1.folderId, a2.folderId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TupleAccountFolder a1 = prev.get(oldItemPosition);
            TupleAccountFolder a2 = next.get(newItemPosition);
            return Objects.equals(a1.order, a2.order) &&
                    a1.primary == a2.primary &&
                    Objects.equals(a1.name, a2.name) &&
                    Objects.equals(a1.color, a2.color) &&

                    Objects.equals(a1.folderId == null ? a1.state : null, a2.folderId == null ? a2.state : null) &&
                    Objects.equals(a1.folderId == null ? a1.last_connected : null, a2.folderId == null ? a2.last_connected : null) &&
                    Objects.equals(a1.folderId == null ? a1.error : null, a2.folderId == null ? a2.error : null) &&

                    Objects.equals(a1.folderId, a2.folderId) &&
                    Objects.equals(a1.folderType, a2.folderType) &&
                    Objects.equals(a1.folderOrder, a2.folderOrder) &&
                    Objects.equals(a1.folderName, a2.folderName) &&
                    Objects.equals(a1.folderDisplay, a2.folderDisplay) &&
                    Objects.equals(a1.folderColor, a2.folderColor) &&
                    Objects.equals(a1.folderSync, a2.folderSync) &&
                    Objects.equals(a1.folderState, a2.folderState) &&
                    Objects.equals(a1.folderSyncState, a2.folderSyncState) &&

                    a1.executing == a2.executing &&
                    a1.messages == a2.messages &&
                    a1.unseen == a2.unseen;
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.item_nav, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.unwire();
        TupleAccountFolder account = items.get(position);
        holder.bindTo(account);
        holder.wire();
    }
}
