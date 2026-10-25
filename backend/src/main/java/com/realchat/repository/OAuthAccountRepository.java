package com.realchat.repository;

import com.realchat.domain.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {
    Optional<OAuthAccount> findByProviderAndProviderSubject(String provider, String providerSubject);
    Optional<OAuthAccount> findByUserIdAndProvider(Long userId, String provider);
    boolean existsByUserIdAndProvider(Long userId, String provider);
    List<OAuthAccount> findByUserId(Long userId);
    long countByUserId(Long userId);
}