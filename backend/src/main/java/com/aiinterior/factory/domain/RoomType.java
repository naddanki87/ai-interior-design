package com.aiinterior.factory.domain;

public enum RoomType {
    LIVING_ROOM("Living room"),
    KITCHEN("Kitchen"),
    UTILITY_BALCONY("Kitchen utility balcony"),
    MASTER_BEDROOM("Master bedroom"),
    CHILDRENS_BEDROOM("Children's bedroom"),
    GUEST_BEDROOM("Guest bedroom"),
    GRANDPARENTS_ROOM("Grandparents' room"),
    BATHROOM("Bathroom"),
    POOJA_ROOM("Pooja room"),
    FOYER("Entrance foyer"),
    BALCONY("Main balcony"),
    DINING_ROOM("Dining room"),
    STUDY("Study room"),
    HOME_OFFICE("Home office");

    private final String label;

    RoomType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean bedroom() {
        return this == MASTER_BEDROOM || this == CHILDRENS_BEDROOM || this == GUEST_BEDROOM || this == GRANDPARENTS_ROOM;
    }
}
