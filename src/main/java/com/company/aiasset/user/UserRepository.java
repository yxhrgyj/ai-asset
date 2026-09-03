package com.company.aiasset.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** 用户名不区分大小写，与数据库的 lower(username) 唯一索引一致。 */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("select case when count(u) > 0 then true else false end from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(User.Role role);
}
