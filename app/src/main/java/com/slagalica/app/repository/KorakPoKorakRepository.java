package com.slagalica.app.repository;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.slagalica.app.model.KorakPoKorakQuestion;

import java.util.Arrays;
import java.util.List;

public class KorakPoKorakRepository {

    private final FirebaseFirestore db;
    private static final String COLLECTION = "korak_po_korak_questions";

    public KorakPoKorakRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void getRandomQuestion(RepositoryCallback<KorakPoKorakQuestion> callback) {
        db.collection(COLLECTION)
            .get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.isEmpty()) {
                    seedAndFetch(callback);
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

    public void getQuestionById(String id, RepositoryCallback<KorakPoKorakQuestion> callback) {
        db.collection(COLLECTION).document(id).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onFailure(new Exception("Question not found")); return; }
                KorakPoKorakQuestion q = doc.toObject(KorakPoKorakQuestion.class);
                if (q != null) q.setId(doc.getId());
                callback.onSuccess(q);
            })
            .addOnFailureListener(callback::onFailure);
    }

    private void seedAndFetch(RepositoryCallback<KorakPoKorakQuestion> callback) {
        Log.d("KPK", "Collection empty, seeding...");
        WriteBatch batch = db.batch();
        for (KorakPoKorakQuestion q : buildQuestions()) {
            DocumentReference ref = db.collection(COLLECTION).document();
            batch.set(ref, q);
        }
        batch.commit()
            .addOnSuccessListener(v -> {
                Log.d("KPK", "Seed successful, fetching question...");
                getRandomQuestion(callback);
            })
            .addOnFailureListener(e -> {
                Log.e("KPK", "Seed failed: " + e.getMessage());
                callback.onFailure(e);
            });
    }

    private List<KorakPoKorakQuestion> buildQuestions() {
        return Arrays.asList(

            // ── Breaking Bad ──────────────────────────────────────────────
            new KorakPoKorakQuestion(null,
                Arrays.asList("Walter White", "Heisenberg", "Walt", "Walter"),
                Arrays.asList(
                    "Holds a PhD in chemistry",
                    "Works as a high school chemistry teacher in Albuquerque",
                    "Diagnosed with terminal lung cancer",
                    "Starts cooking meth to leave money for his family",
                    "Creates the purest methamphetamine ever produced",
                    "Goes by the alias Heisenberg",
                    "The main character of Breaking Bad, played by Bryan Cranston"
                )),

            new KorakPoKorakQuestion(null,
                Arrays.asList("Jesse Pinkman", "Jesse"),
                Arrays.asList(
                    "A young man living in Albuquerque, New Mexico",
                    "A small-time drug dealer with a troubled past",
                    "His chemistry teacher unexpectedly becomes his business partner",
                    "Often says the word 'yeah, b*tch!' as a catchphrase",
                    "Struggles deeply with guilt and drug addiction",
                    "Gets a standalone sequel movie called El Camino",
                    "Played by Aaron Paul in Breaking Bad"
                )),

            new KorakPoKorakQuestion(null,
                Arrays.asList("Saul Goodman", "Saul", "Jimmy McGill", "James McGill", "Goodman"),
                Arrays.asList(
                    "A defense attorney with a very flexible moral compass",
                    "Advertises on cheesy local TV commercials",
                    "Wears colorful suits and talks his way out of anything",
                    "His real name is James McGill",
                    "His catchphrase begins with 'Better call...'",
                    "Represents Walter White and Jesse Pinkman",
                    "Played by Bob Odenkirk in Breaking Bad and Better Call Saul"
                )),

            // ── Daredevil ────────────────────────────────────────────────
            new KorakPoKorakQuestion(null,
                Arrays.asList("Matt Murdock", "Daredevil", "Matt"),
                Arrays.asList(
                    "Blinded as a child after a roadside accident",
                    "His remaining senses were heightened to superhuman levels",
                    "Grew up in Hell's Kitchen, New York",
                    "Works as a defense attorney by day",
                    "Trained in martial arts by a blind master named Stick",
                    "Fights crime as a masked vigilante wearing a red devil costume",
                    "Played by Charlie Cox in Marvel's Daredevil"
                )),

            new KorakPoKorakQuestion(null,
                Arrays.asList("Wilson Fisk", "Kingpin", "Fisk"),
                Arrays.asList(
                    "A massive, physically imposing man with no superpowers",
                    "Has a refined taste for art, fine food and white suits",
                    "Controls New York's criminal underworld from behind the scenes",
                    "Has a tender side — genuinely loves the woman he courts",
                    "His nemesis is a blind vigilante in a red devil costume",
                    "Known as the Kingpin of crime",
                    "Played by Vincent D'Onofrio in Marvel's Daredevil"
                )),

            // ── Avatar (2009) ─────────────────────────────────────────────
            new KorakPoKorakQuestion(null,
                Arrays.asList("Avatar", "Avatar (2009)"),
                Arrays.asList(
                    "A sci-fi blockbuster set in the 22nd century on a distant moon",
                    "Humans travel there to mine a rare mineral called unobtanium",
                    "The indigenous population are tall, blue-skinned beings with tails",
                    "Features a technology that lets humans remotely inhabit alien bodies",
                    "The alien moon is called Pandora, its natives are called Na'vi",
                    "Directed by James Cameron and released in 2009",
                    "The highest-grossing film of all time"
                ))

        );
    }
}
