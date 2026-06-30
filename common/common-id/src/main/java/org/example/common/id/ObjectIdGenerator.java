package org.example.common.id;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

@Component
public class ObjectIdGenerator {

    public String generate() {
        return new ObjectId().toHexString();
    }
}