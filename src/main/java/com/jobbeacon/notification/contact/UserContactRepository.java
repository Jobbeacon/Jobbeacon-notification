package com.jobbeacon.notification.contact;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserContactRepository extends JpaRepository<UserContact, UUID> {

    Optional<UserContact> findByFirebaseUid(String firebaseUid);

    /** Insert or overwrite the whole row: the event carries the user's full current state, last write wins. */
    @Modifying
    @Query(value = """
        INSERT INTO user_contact (user_id, firebase_uid, email, display_name, status, updated_at)
        VALUES (:userId, :firebaseUid, :email, :displayName, :status, :updatedAt)
        ON CONFLICT (user_id) DO UPDATE SET
            firebase_uid = EXCLUDED.firebase_uid,
            email        = EXCLUDED.email,
            display_name = EXCLUDED.display_name,
            status       = EXCLUDED.status,
            updated_at   = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId,
                @Param("firebaseUid") String firebaseUid,
                @Param("email") String email,
                @Param("displayName") String displayName,
                @Param("status") String status,
                @Param("updatedAt") Instant updatedAt);
}
