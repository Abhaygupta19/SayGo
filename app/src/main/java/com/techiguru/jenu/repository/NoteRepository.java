package com.techiguru.jenu.repository;

import android.content.Context;
import com.techiguru.jenu.database.AppDatabase;
import com.techiguru.jenu.database.NoteDao;
import com.techiguru.jenu.model.Note;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteRepository {
    private NoteDao noteDao;
    private ExecutorService executor;

    public NoteRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.noteDao = db.noteDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void insertNote(String content, RepositoryCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                Note note = new Note(content, System.currentTimeMillis());
                noteDao.insert(note);
                callback.onComplete(true);
            } catch (Exception e) {
                callback.onComplete(false);
            }
        });
    }

    public void getAllNotes(RepositoryCallback<List<Note>> callback) {
        executor.execute(() -> {
            List<Note> notes = noteDao.getAllNotes();
            callback.onComplete(notes);
        });
    }

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }
}