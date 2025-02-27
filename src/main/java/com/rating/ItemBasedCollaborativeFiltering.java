package com.rating;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.db.DBConnect;

public class ItemBasedCollaborativeFiltering {

    // Data structure to store item ratings
    private Map<String, Map<String, Double>> itemRatings;
    private int specialistId; // Member variable to store the specialization ID

    public ItemBasedCollaborativeFiltering() {
        this.itemRatings = new HashMap<>();
    }

    // Method to add a rating for an item
    public void addRating(String item, String user, double rating) {
        itemRatings.computeIfAbsent(item, key -> new HashMap<>());
        itemRatings.get(item).put(user, rating);
    }

    // Method to predict the rating for a given item based on other items' ratings
    public double predictRating(String item) {
        double totalRating = 0.0;
        int totalRaters = 0;

        if (itemRatings.containsKey(item)) {
            Map<String, Double> ratingsForItem = itemRatings.get(item);

            for (String user : ratingsForItem.keySet()) {
                double userRating = ratingsForItem.get(user);

                for (String otherItem : itemRatings.keySet()) {
                    if (!otherItem.equals(item)) {
                        Map<String, Double> ratingsForOtherItem = itemRatings.get(otherItem);
                        if (ratingsForOtherItem.containsKey(user)) {
                            double otherItemRating = ratingsForOtherItem.get(user);
                            totalRating += (otherItemRating * userRating);
                            totalRaters++;
                        }
                    }
                }
            }
        }

        if (totalRaters > 0) {
            // Calculate the predicted rating as the average of weighted ratings
            return totalRating / totalRaters;
        } else {
            // Return a default rating if there are no similar items rated by the same users
            return 0.0;
        }
    }

    // Method to calculate and return the best item based on average rating
    public String getBestItem() {
        String bestItem = null;
        double maxAverageRating = Double.MIN_VALUE;

        for (String item : itemRatings.keySet()) {
            Map<String, Double> ratingsForItem = itemRatings.get(item);
            double totalRating = 0.0;
            int numRatings = ratingsForItem.size();

            if (numRatings > 0) {
                for (double rating : ratingsForItem.values()) {
                    totalRating += rating;
                }

                double averageRating = totalRating / numRatings;

                if (averageRating > maxAverageRating) {
                    maxAverageRating = averageRating;
                    bestItem = item;
                }
            }
        }

        // If no ratings are found, assign a valid doctor from the specialization
        if (bestItem == null || bestItem.isEmpty()) {
            bestItem = getDefaultDoctor(null);
        }

        return bestItem;
    }

    // Method to set the specialization ID
    public void setSpecialistId(int sp_id) {
        this.specialistId = sp_id;
    }

    // Method to get the default doctor based on specialization ID and exclude current doctor
    private String getDefaultDoctor(String currentDoctor) {
        String docName = null;
        try (Connection conn = DBConnect.getConn()) {
            String sql;
            if (currentDoctor == null || currentDoctor.isEmpty()) {
                sql = "SELECT full_name FROM doctor_details WHERE specialist = (SELECT sp_name FROM specialist WHERE id = ?) ORDER BY RAND() LIMIT 1";
            } else {
                sql = "SELECT full_name FROM doctor_details WHERE specialist = (SELECT sp_name FROM specialist WHERE id = ?) AND full_name != ? ORDER BY RAND() LIMIT 1";
            }

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, specialistId);
            if (currentDoctor != null && !currentDoctor.isEmpty()) {
                ps.setString(2, currentDoctor);
            }

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                docName = rs.getString(1);
                System.out.println("Selected Doctor: " + docName); // Debug statement
            } else {
                System.out.println("No doctor found for specialization ID: " + specialistId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return docName != null ? docName : "No Available Doctor";
    }

    // Method to get the next best item, excluding the current item
    public String getNextBestItem(String currentItem) {
        List<Map.Entry<String, Double>> itemsByAverageRating = new ArrayList<>(itemRatings.size());

        for (Map.Entry<String, Map<String, Double>> entry : itemRatings.entrySet()) {
            String item = entry.getKey();
            Map<String, Double> ratingsForItem = entry.getValue();
            double totalRating = 0.0;
            int numRatings = ratingsForItem.size();

            if (numRatings > 0) {
                for (double rating : ratingsForItem.values()) {
                    totalRating += rating;
                }

                double averageRating = totalRating / numRatings;
                itemsByAverageRating.add(Map.entry(item, averageRating));
            }
        }

        // Check if there are any ratings
        if (itemsByAverageRating.isEmpty()) {
            // No ratings available, return a default doctor
            String defaultDoctor = getDefaultDoctor(currentItem);
            System.out.println("No ratings found. Returning default doctor: " + defaultDoctor);
            return defaultDoctor;
        }

        // Sort items by average rating in descending order
        itemsByAverageRating.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        int currentIndex = -1;
        for (int i = 0; i < itemsByAverageRating.size(); i++) {
            if (itemsByAverageRating.get(i).getKey().equals(currentItem)) {
                currentIndex = i;
                break;
            }
        }

        // Ensure the rejected doctor is not assigned again
        if (currentIndex != -1 && currentIndex + 1 < itemsByAverageRating.size()) {
            for (int i = currentIndex + 1; i < itemsByAverageRating.size(); i++) {
                String nextBestDoctor = itemsByAverageRating.get(i).getKey();
                if (!nextBestDoctor.equals(currentItem)) {
                    return nextBestDoctor;
                }
            }
        }

        // Return a valid default doctor if no better options are found
        String defaultDoctor = getDefaultDoctor(currentItem);
        System.out.println("Returning default doctor: " + defaultDoctor);
        return defaultDoctor;
    }
}
