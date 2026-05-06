package com.slagalica.app.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.KorakPoKorakQuestion;

import java.util.Arrays;
import java.util.List;

public class KorakPoKorakRepository {

    private final FirebaseFirestore db;
    private static final String COLLECTION = "korak_po_korak_questions";

    public KorakPoKorakRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void seedTestData() {
        List<KorakPoKorakQuestion> questions = Arrays.asList(
            new KorakPoKorakQuestion(null, "Serbia", Arrays.asList(
                "Located in the Balkans",
                "Borders 8 countries",
                "Capital city starts with B",
                "Famous for Tesla",
                "Part of former Yugoslavia",
                "Capital sits on the Sava and Danube rivers",
                "Capital city is Belgrade"
            )),
            new KorakPoKorakQuestion(null, "Olympic Games", Arrays.asList(
                "Held every 4 years",
                "Has summer and winter editions",
                "Symbolized by 5 rings",
                "Originated in ancient Greece",
                "First modern edition was in 1896",
                "2024 host city was Paris",
                "International sporting competition between nations"
            ))
        );

        for (KorakPoKorakQuestion q : questions) {
            db.collection(COLLECTION).add(q);
        }
    }

    public void getRandomQuestion(RepositoryCallback<KorakPoKorakQuestion> callback) {
        db.collection(COLLECTION)
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.isEmpty()) {
                    callback.onFailure(new Exception("No questions available."));
                    return;
                }
                int randomIndex = (int) (Math.random() * snapshot.size());
                KorakPoKorakQuestion question = snapshot.getDocuments()
                    .get(randomIndex)
                    .toObject(KorakPoKorakQuestion.class);
                question.setId(snapshot.getDocuments().get(randomIndex).getId());
                callback.onSuccess(question);
            })
            .addOnFailureListener(callback::onFailure);
    }
}
