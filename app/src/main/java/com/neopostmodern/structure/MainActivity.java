package com.neopostmodern.structure;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloCallback;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.fetcher.ApolloResponseFetchers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.apollographql.apollo.fetcher.ResponseFetcher;
import com.neopostmodern.structure.apollo.NotesQuery;
import com.neopostmodern.structure.apollo.SubmitLinkMutation;
import com.neopostmodern.structure.apollo.ToggleArchivedNoteMutation;

import javax.annotation.Nonnull;

import static com.neopostmodern.structure.apollo.NotesQuery.Note;

public class MainActivity extends AppCompatActivity {
    private RecyclerView notesRecyclerView;
    private ProgressBar progressBar;
    private Toolbar toolbar;
    private RecyclerView.LayoutManager notesLayoutManager;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static String TAG = "MainActivity";

    private List<NotesQuery.Note> notes = Collections.emptyList();
    private boolean showArchivedNotes = false;

    private StructureApplication application;
    ApolloCall<NotesQuery.Data> notesCall;
    Handler uiHandler = new Handler(Looper.getMainLooper());
    MainActivityAdapter notesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        application = (StructureApplication) getApplication();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Log.i(TAG, "Queue URL: " + intent.getStringExtra(Intent.EXTRA_TEXT));
            application.queueUrlToAdd(intent.getStringExtra(Intent.EXTRA_TEXT));
        }

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        progressBar = findViewById(R.id.progressBar);

        notesLayoutManager = new LinearLayoutManager(this);
        notesAdapter = new MainActivityAdapter(this);

        notesRecyclerView = findViewById(R.id.my_recycler_view);
        notesRecyclerView.setHasFixedSize(true);
        notesRecyclerView.setLayoutManager(notesLayoutManager);
        notesRecyclerView.setAdapter(notesAdapter);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        notesRecyclerView.addItemDecoration(itemDecoration);

        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new NoteItemTouchHelper(
                0,
                ItemTouchHelper.LEFT,
                new NoteItemTouchHelper.NoteItemTouchHelperListener() {
                    @Override
                    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
                        Log.d(TAG, "swiped " + position);

                        toggleArchived(position);
                    }
                });
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(notesRecyclerView);


        if (!application.isAuthenticated()) {
            Log.i(TAG, "Will authenticate");
            Intent loginIntent = new Intent(this, LoginActivity.class);
            startActivity(loginIntent);
        } else {
            this.fetchNotes();

            String urlToAdd = application.popUrlToAdd();
            Log.i(TAG, "Popped URL: " + urlToAdd);
            if (urlToAdd != null) {
                addUrl(urlToAdd);
            }
        }

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
//                progressBar.setVisibility(View.VISIBLE);
//                notesRecyclerView.setVisibility(View.GONE);

                fetchNotes(ApolloResponseFetchers.NETWORK_FIRST);
            }
        });

        // Code to Add an item with default animation
        //((MyRecyclerViewAdapter) notesAdapter).addItem(obj, index);

        // Code to remove an item with default animation
        //((MyRecyclerViewAdapter) notesAdapter).deleteItem(index);
    }

    @Override
    protected void onResume() {
        super.onResume();
        notesRecyclerView.setAdapter(notesAdapter);
        notesAdapter.setOnItemClickListener(
                new MainActivityAdapter.MyClickListener() {
                    @Override
                    public void onItemClick(int position, View v) {
                        Log.i(TAG, " Clicked on Item " + position);
                    }

                    @Override
                    public void onLinkClick(int position, View v) {
                        Log.i(TAG, "Clicked on link " + position);
                        String url = notesAdapter.getNote(position).asLink().url();
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_archive:
                showArchivedNotes = !showArchivedNotes;
                item.setChecked(showArchivedNotes);
                feedFilteredNotes();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private ApolloCall.Callback<NotesQuery.Data> dataCallback
            = new ApolloCallback<>(new ApolloCall.Callback<NotesQuery.Data>() {
        @Override public void onResponse(@Nonnull Response<NotesQuery.Data> response) {
            swipeRefreshLayout.setRefreshing(false);

            progressBar.setVisibility(View.GONE);
            notesRecyclerView.setVisibility(View.VISIBLE);

            Log.v(TAG, "got a response");

            consumeNotesResponse(response);
            feedFilteredNotes();
        }

        @Override public void onFailure(@Nonnull ApolloException e) {
            swipeRefreshLayout.setRefreshing(false);

            Log.v(TAG, "got a failure");
            Log.e(TAG, e.getMessage(), e);
        }
    }, uiHandler);

    private void alert(String message, DialogInterface.OnClickListener retry) {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(this);
        }
        builder.setTitle("Error - Retry?")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, retry)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private <T> ApolloCall.Callback<T> apolloCallbackFactory() {
        return this.apolloCallbackFactory(new LimitedApolloCallback<T>() {
            @Override
            public void onSuccess(Response<T> response) {

            }
        });
    }

    private <T> ApolloCall.Callback<T> apolloCallbackFactory(final LimitedApolloCallback<T> limitedApolloCallback) {
        return new ApolloCallback<>(new ApolloCall.Callback<T>() {
            @Override public void onResponse(@Nonnull Response<T> response) {
                Log.v(TAG, "got a response");

                final List<Error> errors = response.errors();
                if (errors.size() > 0) {
                    for (Error error: errors) {
                        alert(
                                error.message(),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        // todo: retry?
                                    }
                                }
                        );
                        Log.e(TAG, error.toString());
                    }
                    return;
                }

                limitedApolloCallback.onSuccess(response);
            }

            @Override public void onFailure(@Nonnull ApolloException e) {
                Log.v(TAG, "got a failure");
                Log.e(TAG, e.getMessage(), e);
            }
        }, uiHandler);
    }

    private interface LimitedApolloCallback<T>  {
        void onSuccess(Response<T> response);
    }

    private void addUrl(String url) {
        SubmitLinkMutation submitLink = SubmitLinkMutation.builder().url(url).build();
        application.apolloClient()
                .mutate(submitLink)
                .enqueue(this.<SubmitLinkMutation.Data>apolloCallbackFactory(new LimitedApolloCallback<SubmitLinkMutation.Data>() {
                    @Override
                    public void onSuccess(Response<SubmitLinkMutation.Data> response) {
                        Toast.makeText(getApplicationContext(), "URL has been added to Structure!",
                                Toast.LENGTH_LONG).show();
                    }
                }));
    }

    private void toggleArchived(final int position) {
        final String _id = notesAdapter.getNote(position)._id();

        ToggleArchivedNoteMutation toggleArchived = ToggleArchivedNoteMutation.builder().noteId(_id).build();
        application.apolloClient()
                .mutate(toggleArchived)
                .enqueue(apolloCallbackFactory(new LimitedApolloCallback<ToggleArchivedNoteMutation.Data>() {
                    @Override
                    public void onSuccess(Response<ToggleArchivedNoteMutation.Data> response) {
                        notesAdapter.deleteItem(position);
                        fetchNotes(ApolloResponseFetchers.NETWORK_FIRST);
//                        Log.d(TAG, "response: " + );
//                        for (int noteIndex = 0; noteIndex < notes.size(); noteIndex++) {
//                            Note note = notes.get(noteIndex);
//                            if (note._id().equals(_id)) {
//                                Log.d(TAG,"Found relevant note " + note);
//                                notes.remove(noteIndex);
//                                notes.add(noteIndex, note);
//                                if (notePassesFilter(note)) {
//                                    notesAdapter.addItem(note, position);
//                                }
//                                break;
//                            }
//                        }
                    }
                }));
    }

    private void consumeNotesResponse(Response<NotesQuery.Data> response) {
        final List<Error> errors = response.errors();
        if (errors.size() > 0) {
            for (Error error: errors) {
                alert(
                        error.message(),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // todo: reload
                            }
                        }
                );
                Log.e(TAG, error.toString());
            }
            return;
        }

        final NotesQuery.Data responseData = response.data();
        if (responseData == null) {
            Log.v(TAG, "no notes");
            return;
        }
        final List<Note> notes = responseData.notes();
        if (notes == null) {
            Log.v(TAG, "empty list");
            return;
        }

        this.notes = notes;
    }

    private boolean notePassesFilter(Note note) {
        return showArchivedNotes || note.archivedAt() == null;
    }

    private void feedFilteredNotes() {
        List<Note> filteredNotes = new ArrayList<>(notes.size());
        for (Note note : notes) {
            if (notePassesFilter(note)) {
                filteredNotes.add(note);
            }
        }
        notesAdapter.setNotes(filteredNotes);
    }

    private void fetchNotes() {
        fetchNotes(ApolloResponseFetchers.CACHE_AND_NETWORK);
    }
    private void fetchNotes(ResponseFetcher networkMode) {
        Log.v(TAG, "fetchNotes");
        final NotesQuery feedQuery = NotesQuery.builder()
//                .limit(10)
                .build();
        notesCall = application.apolloClient()
                .query(feedQuery)
                .responseFetcher(networkMode);
        notesCall.enqueue(dataCallback);
    }
}
