package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.entity.AppUser;
import com.ecycle.backend.repository.UserRepository;
import com.ecycle.shared.Enums.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the bootstrap moderator (a RESOLVER; the platform has only SELLER, BUYER and RESOLVER roles) from environment settings and,
 * only when SEED_DEMO=true, the demo dataset. Not transactional on purpose: see DemoDataSeeder.
 */
@Component @RequiredArgsConstructor
public class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);
    private final AppProps props; private final UserRepository users; private final AuthService auth; private final DemoDataSeeder demo;

    @Override
    public void run(ApplicationArguments args) {
        AppUser moderator = users.findByEmailIgnoreCase(props.getAdminEmail()).orElse(null);
        if (moderator == null) {
            if (props.getAdminPassword().isBlank()) log.warn("ADMIN_PASSWORD is not set: no bootstrap moderator created. Set ADMIN_EMAIL/ADMIN_PASSWORD to create one.");
            else { moderator = auth.createUser(props.getAdminEmail(), props.getAdminPassword(), "Platform Moderator", null, Role.RESOLVER); log.info("Bootstrap moderator (resolver) created: {}", props.getAdminEmail()); }
        }
        if (!props.isSeedDemo()) return;
        if (users.findByEmailIgnoreCase("seller@ecycle.local").isPresent()) {
            demo.backfillExistingDemo();
            return;
        }
        demo.seed(moderator);
    }
}
