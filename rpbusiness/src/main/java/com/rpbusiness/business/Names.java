package com.rpbusiness.business;

import net.minecraft.util.RandomSource;

public final class Names {
    private static final String[] FIRST = {
            "Ahmed", "Sara", "Omar", "Layla", "Yusuf", "Mariam", "Ali", "Noor", "Hassan", "Fatima", "Karim", "Huda",
            "Liam", "Emma", "Noah", "Olivia", "Lucas", "Mia", "Leo", "Sofia", "Ethan", "Chloe", "Adam", "Lina",
            "Mateo", "Elena", "Kenji", "Aiko", "Ravi", "Priya", "Ivan", "Anya", "Tariq", "Rania", "Samir", "Yara",
            "Jonas", "Hana", "Diego", "Nadia", "Felix", "Zara", "Malik", "Amira", "Theo", "Ines", "Rami", "Dina"};
    private static final String[] LAST = {
            "Haddad", "Mansour", "Khalil", "Nasser", "Saleh", "Farouk", "Rahman", "Aziz", "Hamdan", "Yassin",
            "Smith", "Garcia", "Muller", "Rossi", "Silva", "Novak", "Tanaka", "Kim", "Dubois", "Jensen",
            "Costa", "Moreau", "Ivanov", "Patel", "Lopez", "Weber", "Sato", "Hughes", "Berg", "Ortiz"};

    public static String random(RandomSource r) {
        return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
    }
    private Names() {}
}
