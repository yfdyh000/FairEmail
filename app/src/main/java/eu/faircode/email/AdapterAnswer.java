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
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class AdapterAnswer extends RecyclerView.Adapter<AdapterAnswer.ViewHolder> {
    private Fragment parentFragment;

    private Context context;
    private LifecycleOwner owner;
    private LayoutInflater inflater;

    private DateFormat DF;
    private NumberFormat NF = NumberFormat.getNumberInstance();

    private String search = null;
    private List<EntityAnswer> all = new ArrayList<>();
    private List<EntityAnswer> selected = new ArrayList<>();

    private boolean composable = false;

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private View view;
        private TextView tvName;
        private TextView tvGroup;
        private ImageView ivExternal;
        private ImageView ivStandard;
        private ImageView ivFavorite;
        private ImageView ivReceipt;
        private TextView tvLastApplied;
        private TextView tvApplied;

        private TwoStateOwner powner = new TwoStateOwner(owner, "RulePopup");

        ViewHolder(View itemView) {
            super(itemView);

            view = itemView.findViewById(R.id.clItem);
            tvName = itemView.findViewById(R.id.tvName);
            tvGroup = itemView.findViewById(R.id.tvGroup);
            ivExternal = itemView.findViewById(R.id.ivExternal);
            ivStandard = itemView.findViewById(R.id.ivStandard);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
            ivReceipt = itemView.findViewById(R.id.ivReceipt);
            tvLastApplied = itemView.findViewById(R.id.tvLastApplied);
            tvApplied = itemView.findViewById(R.id.tvApplied);
        }

        private void wire() {
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }

        private void unwire() {
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
        }

        private void bindTo(EntityAnswer answer) {
            view.setAlpha(answer.hide ? Helper.LOW_LIGHT : 1.0f);
            tvName.setText(answer.name);
            tvGroup.setText(answer.group);
            tvGroup.setVisibility(TextUtils.isEmpty(answer.group) ? View.GONE : View.VISIBLE);
            ivExternal.setVisibility(answer.external ? View.VISIBLE : View.GONE);
            ivStandard.setVisibility(answer.standard ? View.VISIBLE : View.GONE);
            ivFavorite.setVisibility(answer.favorite ? View.VISIBLE : View.GONE);
            ivReceipt.setVisibility(answer.receipt ? View.VISIBLE : View.GONE);
            tvLastApplied.setText(answer.last_applied == null ? null : DF.format(answer.last_applied));
            tvApplied.setText(NF.format(answer.applied));
        }

        @Override
        public void onClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;

            EntityAnswer answer = selected.get(pos);

            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
            lbm.sendBroadcast(
                    new Intent(ActivityView.ACTION_EDIT_ANSWER)
                            .putExtra("id", answer.id));
        }

        @Override
        public boolean onLongClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return false;

            final EntityAnswer answer = selected.get(pos);

            PopupMenuLifecycle popupMenu = new PopupMenuLifecycle(context, powner, view);

            SpannableString ss = new SpannableString(answer.name);
            ss.setSpan(new StyleSpan(Typeface.ITALIC), 0, ss.length(), 0);
            ss.setSpan(new RelativeSizeSpan(0.9f), 0, ss.length(), 0);
            popupMenu.getMenu().add(Menu.NONE, 0, 0, ss).setEnabled(false);

            if (composable)
                popupMenu.getMenu().add(Menu.NONE, R.string.title_compose, 1, R.string.title_compose);
            popupMenu.getMenu().add(Menu.NONE, R.string.title_answer_favorite, 2, R.string.title_answer_favorite)
                    .setCheckable(true).setChecked(answer.favorite);
            popupMenu.getMenu().add(Menu.NONE, R.string.title_answer_hide, 3, R.string.title_answer_hide)
                    .setCheckable(true).setChecked(answer.hide);
            popupMenu.getMenu().add(Menu.NONE, R.string.title_reset, 4, R.string.title_reset);
            popupMenu.getMenu().add(Menu.NONE, R.string.title_copy, 5, R.string.title_copy);

            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.string.title_compose) {
                        onActionCompose();
                        return true;
                    } else if (itemId == R.string.title_answer_favorite) {
                        onActionFavorite(!item.isChecked());
                        return true;
                    } else if (itemId == R.string.title_answer_hide) {
                        onActionHide(!item.isChecked());
                        return true;
                    } else if (itemId == R.string.title_reset) {
                        onActionReset();
                        return true;
                    } else if (itemId == R.string.title_copy) {
                        onActionCopy();
                        return true;
                    }
                    return false;
                }

                private void onActionCompose() {
                    context.startActivity(new Intent(context, ActivityCompose.class)
                            .putExtra("action", "new")
                            .putExtra("answer", answer.id));
                }

                private void onActionFavorite(boolean favorite) {
                    Bundle args = new Bundle();
                    args.putLong("id", answer.id);
                    args.putBoolean("favorite", favorite);

                    new SimpleTask<Boolean>() {
                        @Override
                        protected Boolean onExecute(Context context, Bundle args) {
                            long id = args.getLong("id");
                            boolean favorite = args.getBoolean("favorite");

                            DB db = DB.getInstance(context);
                            db.answer().setAnswerFavorite(id, favorite);

                            return favorite;
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                        }
                    }.execute(context, owner, args, "answer:favorite");
                }

                private void onActionHide(boolean hide) {
                    Bundle args = new Bundle();
                    args.putLong("id", answer.id);
                    args.putBoolean("hide", hide);

                    new SimpleTask<Boolean>() {
                        @Override
                        protected Boolean onExecute(Context context, Bundle args) {
                            long id = args.getLong("id");
                            boolean hide = args.getBoolean("hide");

                            DB db = DB.getInstance(context);
                            db.answer().setAnswerHidden(id, hide);

                            return hide;
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                        }
                    }.execute(context, owner, args, "answer:hide");
                }

                private void onActionReset() {
                    Bundle args = new Bundle();
                    args.putLong("id", answer.id);

                    new SimpleTask<Void>() {
                        @Override
                        protected Void onExecute(Context context, Bundle args) {
                            long id = args.getLong("id");

                            DB db = DB.getInstance(context);
                            db.answer().resetAnswer(id);

                            return null;
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                        }
                    }.execute(context, owner, args, "answer:reset");
                }

                private void onActionCopy() {
                    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
                    lbm.sendBroadcast(
                            new Intent(ActivityView.ACTION_EDIT_ANSWER)
                                    .putExtra("id", answer.id)
                                    .putExtra("copy", true));
                }
            });

            popupMenu.show();

            return true;
        }
    }

    AdapterAnswer(final Fragment parentFragment) {
        this.parentFragment = parentFragment;

        this.context = parentFragment.getContext();
        this.owner = parentFragment.getViewLifecycleOwner();
        this.inflater = LayoutInflater.from(context);

        this.DF = Helper.getDateTimeInstance(this.context);

        setHasStableIds(true);

        owner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroyed() {
                Log.d(AdapterAnswer.this + " parent destroyed");
                AdapterAnswer.this.parentFragment = null;
                owner.getLifecycle().removeObserver(this);
            }
        });

        new SimpleTask<Boolean>() {
            @Override
            protected Boolean onExecute(Context context, Bundle args) {
                DB db = DB.getInstance(context);
                List<TupleIdentityEx> identities = db.identity().getComposableIdentities(null);
                return (identities != null && identities.size() > 0);
            }

            @Override
            protected void onExecuted(Bundle args, Boolean composable) {
                AdapterAnswer.this.composable = composable;
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
            }
        }.execute(context, owner, new Bundle(), "answer:composable");
    }

    public void set(@NonNull List<EntityAnswer> answers) {
        Log.i("Set answers=" + answers.size() + " search=" + search);

        all = answers;

        List<EntityAnswer> items;
        if (TextUtils.isEmpty(search))
            items = all;
        else {
            items = new ArrayList<>();
            String query = search.toLowerCase().trim();
            for (EntityAnswer answer : answers) {
                if (answer.name.toLowerCase().contains(query) ||
                        (answer.group != null && answer.group.toLowerCase().contains(query)) ||
                        answer.text.toLowerCase().contains(query))
                    items.add(answer);
            }
        }

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(selected, items), false);

        selected = items;

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

    public void search(String query) {
        Log.i("Answers query=" + query);
        search = query;
        set(all);
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private List<EntityAnswer> prev = new ArrayList<>();
        private List<EntityAnswer> next = new ArrayList<>();

        DiffCallback(List<EntityAnswer> prev, List<EntityAnswer> next) {
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
            EntityAnswer a1 = prev.get(oldItemPosition);
            EntityAnswer a2 = next.get(newItemPosition);
            return a1.id.equals(a2.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            EntityAnswer a1 = prev.get(oldItemPosition);
            EntityAnswer a2 = next.get(newItemPosition);
            return a1.equals(a2);
        }
    }

    @Override
    public long getItemId(int position) {
        return selected.get(position).id;
    }

    @Override
    public int getItemCount() {
        return selected.size();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.item_answer, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EntityAnswer answer = selected.get(position);
        holder.powner.recreate(answer == null ? null : answer.id);

        holder.unwire();
        holder.bindTo(answer);
        holder.wire();
    }
}
