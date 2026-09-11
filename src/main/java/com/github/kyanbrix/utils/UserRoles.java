package com.github.kyanbrix.utils;

public enum UserRoles {


    ADMIN(1469573549076250665L),
    STAFF(1479844227008696340L),
    BOOSTER(1474656723078746155L),
    DEV_ID(683613536823279794L),
    OWNER_ID(909056906188972053L);

    private final long id;

    UserRoles(long l) {

        this.id = l;

    }

    public long getId() {
        return id;
    }
}
