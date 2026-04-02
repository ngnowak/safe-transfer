package com.nn.safetransfer.common.domain.result;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Empty {
    private static final Empty INSTANCE = new Empty();

    public static Empty getInstance() {
        return INSTANCE;
    }
}
