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

    Copyright 2018-2022 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AdapterNavSearch extends RecyclerView.Adapter<AdapterNavSearch.ViewHolder> {
    private Context context;
    private LifecycleOwner owner;
    private FragmentManager manager;
    private LayoutInflater inflater;

    private boolean expanded = true;
    private List<EntitySearch> items = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
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
        }

        private void unwire() {
            view.setOnClickListener(null);
        }

        private void bindTo(EntitySearch search) {
            ivItem.setImageResource(R.drawable.twotone_search_24);
            if (search.color == null)
                ivItem.clearColorFilter();
            else
                ivItem.setColorFilter(search.color);

            ivBadge.setVisibility(View.GONE);
            tvItem.setText(search.name);

            tvItemExtra.setVisibility(View.GONE);
            ivExtra.setVisibility(View.GONE);
            ivWarning.setVisibility(View.GONE);
        }

        @Override
        public void onClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;

            EntitySearch search = items.get(pos);
            if (search == null)
                return;

            try {
                JSONObject json = new JSONObject(search.data);
                BoundaryCallbackMessages.SearchCriteria criteria =
                        BoundaryCallbackMessages.SearchCriteria.fromJSON(json);
                criteria.id = search.id;
                FragmentMessages.search(
                        context, owner, manager,
                        -1L, -1L, false, criteria);
            } catch (Throwable ex) {
                Log.e(ex);
            }
        }
    }

    AdapterNavSearch(Context context, LifecycleOwner owner, FragmentManager manager) {
        this.context = context;
        this.owner = owner;
        this.manager = manager;
        this.inflater = LayoutInflater.from(context);

        setHasStableIds(true);

        owner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroyed() {
                Log.d(AdapterNavSearch.this + " parent destroyed");
                AdapterNavSearch.this.manager = null;
                owner.getLifecycle().removeObserver(this);
            }
        });
    }

    public void set(@NonNull List<EntitySearch> search, boolean expanded) {
        Log.i("Set nav search=" + search.size() + " expanded=" + expanded);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, search), false);

        this.expanded = expanded;
        this.items = search;

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

    EntitySearch get(int pos) {
        return items.get(pos);
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private List<EntitySearch> prev = new ArrayList<>();
        private List<EntitySearch> next = new ArrayList<>();

        DiffCallback(List<EntitySearch> prev, List<EntitySearch> next) {
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
            EntitySearch s1 = prev.get(oldItemPosition);
            EntitySearch s2 = next.get(newItemPosition);
            return s1.id.equals(s2.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            EntitySearch s1 = prev.get(oldItemPosition);
            EntitySearch s2 = next.get(newItemPosition);
            return s1.equals(s2);
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
        EntitySearch search = items.get(position);
        holder.bindTo(search);
        holder.wire();
    }
}
