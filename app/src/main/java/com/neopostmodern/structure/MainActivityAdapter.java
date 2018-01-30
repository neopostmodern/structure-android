package com.neopostmodern.structure;

import android.content.Context;
import android.graphics.Color;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.neopostmodern.structure.apollo.NotesQuery;
import com.neopostmodern.structure.apollo.type.NoteType;

import java.util.Collections;
import java.util.List;

public class MainActivityAdapter extends RecyclerView.Adapter<MainActivityAdapter.NoteObjectHolder>{
    private static String TAG = "MainActivityAdapter";
    private List<NotesQuery.Note> notes = Collections.emptyList();
    private static MyClickListener clickListener;

    private Context context;

    public static class NoteObjectHolder extends RecyclerView.ViewHolder
            implements View
            .OnClickListener {
        private final ImageButton button;
        TextView nameView;
        TextView urlView;
        ConstraintLayout foreground;
        FlexboxLayout tags;

        TextView archiveTextView;
        ImageView archiveImageView;

        NoteObjectHolder(View itemView) {
            super(itemView);
            foreground = itemView.findViewById(R.id.foreground);

            nameView = itemView.findViewById(R.id.textView);
            urlView = itemView.findViewById(R.id.urlTextView);
            tags = itemView.findViewById(R.id.tags);
            button = itemView.findViewById(R.id.button);

            archiveImageView = itemView.findViewById(R.id.archiveImageView);
            archiveTextView = itemView.findViewById(R.id.archiveTextView);

            Log.i(TAG, "Adding Listener");
            itemView.setOnClickListener(this);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickListener.onLinkClick(getAdapterPosition(), v);
                }
            });
        }

        @Override
        public void onClick(View v) {
            clickListener.onItemClick(getPosition(), v);
        }
    }
    void setOnItemClickListener(MyClickListener myClickListener) {
        this.clickListener = myClickListener;
    }

    MainActivityAdapter(Context context) {
        this.context = context;
    }

    @Override
    public NoteObjectHolder onCreateViewHolder(ViewGroup parent,
                                               int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.notelist_item, parent, false);

        return new NoteObjectHolder(view);
    }

    @Override
    public void onBindViewHolder(NoteObjectHolder holder, int position) {
        NotesQuery.Note note = notes.get(position);
        if (note.type() != NoteType.LINK) {
            holder.button.setVisibility(View.INVISIBLE);
            holder.urlView.setText("Text note");
        } else {
            holder.button.setVisibility(View.VISIBLE);
            holder.urlView.setText(note.asLink().url());
        }
        holder.nameView.setText(note.name());

        if (note.archivedAt() != null) {  // item is archived
            holder.nameView.setTextColor(Color.GRAY);

            holder.archiveImageView.setImageResource(R.drawable.ic_unarchive_white_24dp);
            holder.archiveTextView.setText(R.string.action_unarchive);
        } else { // item is note archived
            holder.nameView.setTextColor(Color.BLACK);

            holder.archiveImageView.setImageResource(R.drawable.ic_archive_white_24dp);
            holder.archiveTextView.setText(R.string.action_archive);
        }

        holder.tags.removeAllViews();
        for (int index = 0; index < note.tags().size(); index++) {
            NotesQuery.Tag tag = note.tags().get(index);

            TextView tagView = new TextView(context);
            tagView.setText(tag.name());
            LinearLayout.LayoutParams tagLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagLayout.setMargins(16, 0, 0, 16);
            tagView.setLayoutParams(tagLayout);
            tagView.setPadding(16, 8, 16, 8);

            int backgroundColor;
            try {
                backgroundColor = Color.parseColor(tag.color());
            } catch (IllegalArgumentException exception) {
                Log.e(TAG, "Illegal color: " + tag.color());
                Log.e(TAG, "caused a IllegalArgumentException: " + exception.getMessage());
                backgroundColor = Color.GRAY;
            }
            tagView.setBackgroundColor(backgroundColor);

            double perceivedLightness = (0.299 * Color.red(backgroundColor) + 0.587 * Color.green(backgroundColor) + 0.144 * Color.blue(backgroundColor)) / 255;
            if (perceivedLightness < 0.5) {
                tagView.setTextColor(Color.parseColor("#EEEEEE"));
            } else {
                tagView.setTextColor(Color.parseColor("#333333"));
            }
            holder.tags.addView(tagView);
        }
    }

    public void setNotes(List<NotesQuery.Note> notes) {
        this.notes = notes;
        this.notifyDataSetChanged();
    }

    public NotesQuery.Note getNote(int position) {
        return notes.get(position);
    }

    public void addItem(NotesQuery.Note dataObj, int index) {
        notes.add(dataObj);
        notifyItemInserted(index);
    }

    public void deleteItem(int index) {
        notes.remove(index);
        notifyItemRemoved(index);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public interface MyClickListener {
        void onItemClick(int position, View v);
        void onLinkClick(int position, View v);
    }
}
