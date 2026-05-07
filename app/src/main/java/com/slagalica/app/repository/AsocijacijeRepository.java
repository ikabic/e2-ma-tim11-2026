package com.slagalica.app.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.AsocijacijeQuestion;

import java.util.Arrays;
import java.util.List;

public class AsocijacijeRepository {

    private final FirebaseFirestore db;
    private static final String COLLECTION = "asocijacije_questions";

    public AsocijacijeRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void seedTestData() {

        AsocijacijeQuestion q1 = new AsocijacijeQuestion(
                null,
                Arrays.asList("Red", "Largest", "Gas giant", "Jupiter"),
                Arrays.asList("Rocky", "Closest to Sun", "Craters", "Mercury"),
                Arrays.asList("Rings", "Saturn", "Ice", "Cassini"),
                Arrays.asList("Moon", "Life", "Blue", "Water"),
                Arrays.asList("Gas Giants", "Rocky Planets", "Ringed Planets", "Earth-like"),
                "Planets"
        );

        AsocijacijeQuestion q2 = new AsocijacijeQuestion(
                null,
                Arrays.asList("Federer", "Wimbledon", "Racket", "Serve"),
                Arrays.asList("Messi", "Goal", "Pitch", "Dribble"),
                Arrays.asList("Michael Jordan", "Slam dunk", "Hoop", "NBA"),
                Arrays.asList("Phelps", "Pool", "Butterfly", "Stroke"),
                Arrays.asList("Tennis", "Football", "Basketball", "Swimming"),
                "Sports"
        );

        AsocijacijeQuestion q3 = new AsocijacijeQuestion(
                null,
                Arrays.asList("Eiffel Tower", "France", "Seine", "Baguette"),
                Arrays.asList("Big Ben", "Thames", "UK", "Royals"),
                Arrays.asList("Colosseum", "Vatican", "Italy", "Pasta"),
                Arrays.asList("Acropolis", "Greece", "Olympics origin", "Parthenon"),
                Arrays.asList("Paris", "London", "Rome", "Athens"),
                "Capital Cities"
        );

        for (AsocijacijeQuestion q : Arrays.asList(q1, q2, q3)) {
            db.collection(COLLECTION).add(q);
        }
    }

    public void getRandomQuestion(RepositoryCallback<AsocijacijeQuestion> callback) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onFailure(new Exception("No Asocijacije questions available."));
                        return;
                    }
                    int idx = (int) (Math.random() * snapshot.size());
                    AsocijacijeQuestion q = snapshot.getDocuments()
                            .get(idx)
                            .toObject(AsocijacijeQuestion.class);
                    if (q != null) q.setId(snapshot.getDocuments().get(idx).getId());
                    callback.onSuccess(q);
                })
                .addOnFailureListener(callback::onFailure);
    }
}