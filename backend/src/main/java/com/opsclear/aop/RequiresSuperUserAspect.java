package com.opsclear.aop;

import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.UserModel;
import com.opsclear.repository.UserRepository;
import com.opsclear.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class RequiresSuperUserAspect {

    private final UserRepository userRepository;

    @Around("@annotation(com.opsclear.aop.RequiresSuperUser)")
    public Object checkSuperUser(ProceedingJoinPoint pjp) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = SecurityUtils.resolveUserId(auth);

        boolean isSuperUser = userRepository.findById(userId)
                .map(UserModel::isSuperUser)
                .orElse(false);
        if (!isSuperUser) {
            throw new ForbiddenException("Super admin access required");
        }

        return pjp.proceed();
    }
}
