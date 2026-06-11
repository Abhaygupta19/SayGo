package com.techiguru.jenuabhay.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.techiguru.jenuabhay.model.Note;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    void insert(Note note);

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    List<Note> getAllNotes();
}