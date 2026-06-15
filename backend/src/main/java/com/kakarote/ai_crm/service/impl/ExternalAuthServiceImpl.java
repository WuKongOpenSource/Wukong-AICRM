package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.config.ExternalAuthProperties;
import com.kakarote.ai_crm.config.OidcConfig;
import com.kakarote.ai_crm.config.security.service.TokenService;
import com.kakarote.ai_crm.entity.BO.ExternalAuthTicketLoginBO;
import com.kakarote.ai_crm.entity.BO.LoginUser;
import com.kakarote.ai_crm.entity.PO.ExternalAuthIdentity;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.entity.VO.ExternalAuthAuthorizeVO;
import com.kakarote.ai_crm.entity.VO.ExternalAuthBindingVO;
import com.kakarote.ai_crm.entity.VO.ExternalAuthProviderVO;
import com.kakarote.ai_crm.entity.VO.ManageUserVO;
import com.kakarote.ai_crm.mapper.ExternalAuthIdentityMapper;
import com.kakarote.ai_crm.service.ExternalAuthService;
import com.kakarote.ai_crm.service.FileStorageService;
import com.kakarote.ai_crm.service.ManageUserService;
import com.kakarote.ai_crm.service.OidcService;
import com.kakarote.ai_crm.service.support.UserPreferenceSupport;
import com.kakarote.ai_crm.utils.UserUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ExternalAuthServiceImpl extends ServiceImpl<ExternalAuthIdentityMapper, ExternalAuthIdentity> implements ExternalAuthService {

    private static final String STATE_KEY_PREFIX = "external_auth:state:";
    private static final String TICKET_KEY_PREFIX = "external_auth:ticket:";
    private static final String SCENE_LOGIN = "login";
    private static final String SCENE_BIND = "bind";
    private static final List<String> SUPPORTED_PROVIDERS = List.of("google", "wechat");

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private ExternalAuthProperties externalAuthProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ManageUserService manageUserService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private OidcService oidcService;

    @Autowired
    private OidcConfig oidcConfig;

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    public List<ExternalAuthProviderVO> listProviders() {
        List<ExternalAuthProviderVO> providers = new ArrayList<>();
        for (String provider : SUPPORTED_PROVIDERS) {
            ExternalAuthProperties.ProviderConfig config = externalAuthProperties.getProvider(provider);
            providers.add(new ExternalAuthProviderVO(provider, providerName(provider), config != null && config.isUsable()));
        }
        return providers;
    }

    @Override
    public ExternalAuthAuthorizeVO createAuthorizeUrl(String provider, String redirect, HttpServletRequest request) {
        String normalizedProvider = normalizeProvider(provider);
        ExternalAuthProperties.ProviderConfig config = requireUsableProvider(normalizedProvider);
        String redirectUri = resolveProviderCallbackUri(normalizedProvider, config, request);
        AuthState authState = createState(normalizedProvider, SCENE_LOGIN, null, redirect, request);
        return new ExternalAuthAuthorizeVO(normalizedProvider, buildProviderAuthorizeUrl(normalizedProvider, config, redirectUri, authState.getState()));
    }

    @Override
    public ExternalAuthAuthorizeVO createBindAuthorizeUrl(String provider, String redirect, HttpServletRequest request) {
        Long userId = UserUtil.getUserId();
        if (userId == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        String normalizedProvider = normalizeProvider(provider);
        ExternalAuthProperties.ProviderConfig config = requireUsableProvider(normalizedProvider);
        String redirectUri = resolveProviderCallbackUri(normalizedProvider, config, request);
        AuthState authState = createState(normalizedProvider, SCENE_BIND, userId, redirect, request);
        return new ExternalAuthAuthorizeVO(normalizedProvider, buildProviderAuthorizeUrl(normalizedProvider, config, redirectUri, authState.getState()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleCallback(String provider, String code, String state, String error, HttpServletRequest request) {
        String normalizedProvider = normalizeProvider(provider);
        String fallbackRedirect = defaultFrontendRedirect(request);

        AuthState authState = consumeState(state);
        String frontendRedirect = authState == null ? fallbackRedirect : authState.getRedirect();
        if (authState == null || !Objects.equals(authState.getProvider(), normalizedProvider)) {
            return appendFrontendQuery(frontendRedirect, Map.of("externalAuthError", "invalid_state", "provider", normalizedProvider));
        }

        if (StrUtil.isNotBlank(error) || StrUtil.isBlank(code)) {
            return appendFrontendQuery(frontendRedirect, Map.of("externalAuthError", "denied", "provider", normalizedProvider));
        }

        try {
            ExternalAuthProperties.ProviderConfig config = requireUsableProvider(normalizedProvider);
            ExternalProfile profile = fetchExternalProfile(normalizedProvider, config, code, resolveProviderCallbackUri(normalizedProvider, config, request));
            if (SCENE_BIND.equals(authState.getScene())) {
                bindIdentity(normalizedProvider, profile, authState.getUserId());
                return appendFrontendQuery(frontendRedirect, Map.of("externalBind", "success", "provider", normalizedProvider));
            }

            Long userId = resolveLoginUserId(normalizedProvider, profile);
            if (userId == null) {
                return appendFrontendQuery(frontendRedirect, Map.of("externalAuthError", "unbound", "provider", normalizedProvider));
            }

            String ticket = createLoginTicket(userId);
            return appendFrontendQuery(frontendRedirect, Map.of("externalLoginTicket", ticket, "provider", normalizedProvider));
        } catch (Exception ex) {
            log.warn("External auth callback failed for provider={}", normalizedProvider, ex);
            return appendFrontendQuery(frontendRedirect, Map.of("externalAuthError", "failed", "provider", normalizedProvider));
        }
    }

    @Override
    public Map<String, Object> loginByTicket(ExternalAuthTicketLoginBO loginBO, HttpServletResponse response) {
        String ticketKey = TICKET_KEY_PREFIX + loginBO.getTicket();
        String userIdText = redisTemplate.opsForValue().get(ticketKey);
        redisTemplate.delete(ticketKey);
        if (StrUtil.isBlank(userIdText)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NOT_LOGIN, "External login ticket is expired");
        }
        ManagerUser user = loadEnabledUser(Long.parseLong(userIdText));
        return createLoginResult(user, response);
    }

    @Override
    public List<ExternalAuthBindingVO> listBindings() {
        Long userId = UserUtil.getUserId();
        if (userId == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        List<ExternalAuthIdentity> identities = lambdaQuery()
                .eq(ExternalAuthIdentity::getUserId, userId)
                .eq(ExternalAuthIdentity::getStatus, 1)
                .list();
        return identities.stream().map(this::toBindingVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String provider) {
        Long userId = UserUtil.getUserId();
        if (userId == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        String normalizedProvider = normalizeProvider(provider);
        lambdaUpdate()
                .eq(ExternalAuthIdentity::getProvider, normalizedProvider)
                .eq(ExternalAuthIdentity::getUserId, userId)
                .remove();
    }

    private AuthState createState(String provider, String scene, Long userId, String redirect, HttpServletRequest request) {
        AuthState state = new AuthState();
        state.setState(IdUtil.fastSimpleUUID());
        state.setProvider(provider);
        state.setScene(scene);
        state.setUserId(userId);
        state.setRedirect(sanitizeFrontendRedirect(redirect, request));
        redisTemplate.opsForValue().set(
                STATE_KEY_PREFIX + state.getState(),
                JSON.toJSONString(state),
                externalAuthProperties.getStateTtlSeconds(),
                TimeUnit.SECONDS
        );
        return state;
    }

    private AuthState consumeState(String state) {
        if (StrUtil.isBlank(state)) {
            return null;
        }
        String key = STATE_KEY_PREFIX + state;
        String value = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return JSON.parseObject(value, AuthState.class);
    }

    private String createLoginTicket(Long userId) {
        String ticket = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(
                TICKET_KEY_PREFIX + ticket,
                String.valueOf(userId),
                externalAuthProperties.getTicketTtlSeconds(),
                TimeUnit.SECONDS
        );
        return ticket;
    }

    private Long resolveLoginUserId(String provider, ExternalProfile profile) {
        ExternalAuthIdentity identity = findIdentityBySubject(provider, profile.getSubject());
        if (identity != null && Objects.equals(identity.getStatus(), 1)) {
            identity.setLastLoginTime(new Date());
            updateById(identity);
            return identity.getUserId();
        }

        ManagerUser user = findSingleEnabledUserByEmail(profile);
        if (user == null) {
            return null;
        }
        ExternalAuthIdentity boundIdentity = bindIdentity(provider, profile, user.getUserId());
        boundIdentity.setLastLoginTime(new Date());
        updateById(boundIdentity);
        return user.getUserId();
    }

    private ExternalAuthIdentity bindIdentity(String provider, ExternalProfile profile, Long userId) {
        if (userId == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NOT_LOGIN);
        }
        loadEnabledUser(userId);
        ExternalAuthIdentity subjectIdentity = findIdentityBySubject(provider, profile.getSubject());
        if (subjectIdentity != null && !Objects.equals(subjectIdentity.getUserId(), userId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "External account is already bound");
        }

        ExternalAuthIdentity userIdentity = findIdentityByUser(provider, userId);
        ExternalAuthIdentity identity = subjectIdentity != null ? subjectIdentity : userIdentity;
        if (identity == null) {
            identity = new ExternalAuthIdentity();
            identity.setProvider(provider);
            identity.setUserId(userId);
            identity.setBindTime(new Date());
            identity.setStatus(1);
        }
        identity.setSubject(profile.getSubject());
        identity.setEmail(profile.getEmail());
        identity.setEmailVerified(profile.getEmailVerified());
        identity.setDisplayName(profile.getDisplayName());
        identity.setAvatarUrl(profile.getAvatarUrl());
        identity.setRawProfile(profile.getRawProfile());
        identity.setStatus(1);
        saveOrUpdate(identity);
        return identity;
    }

    private ExternalAuthIdentity findIdentityBySubject(String provider, String subject) {
        return lambdaQuery()
                .eq(ExternalAuthIdentity::getProvider, provider)
                .eq(ExternalAuthIdentity::getSubject, subject)
                .last("LIMIT 1")
                .one();
    }

    private ExternalAuthIdentity findIdentityByUser(String provider, Long userId) {
        return lambdaQuery()
                .eq(ExternalAuthIdentity::getProvider, provider)
                .eq(ExternalAuthIdentity::getUserId, userId)
                .last("LIMIT 1")
                .one();
    }

    private ManagerUser findSingleEnabledUserByEmail(ExternalProfile profile) {
        if (!Boolean.TRUE.equals(profile.getEmailVerified()) || StrUtil.isBlank(profile.getEmail())) {
            return null;
        }
        List<ManagerUser> users = manageUserService.lambdaQuery()
                .eq(ManagerUser::getEmail, profile.getEmail())
                .eq(ManagerUser::getStatus, 1)
                .last("LIMIT 2")
                .list();
        return users.size() == 1 ? users.get(0) : null;
    }

    private ManagerUser loadEnabledUser(Long userId) {
        ManagerUser user = manageUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST);
        }
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DISABLED);
        }
        return user;
    }

    private Map<String, Object> createLoginResult(ManagerUser user, HttpServletResponse response) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUser(user);
        String token = tokenService.createToken(loginUser);

        String sessionId = oidcService.createSession(loginUser);
        ResponseCookie sessionCookie = ResponseCookie.from(oidcConfig.getSessionCookie(), sessionId)
                .httpOnly(true)
                .path("/")
                .maxAge(oidcConfig.getTokenExpiry())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", toManageUserVO(user));
        return result;
    }

    private ManageUserVO toManageUserVO(ManagerUser user) {
        ManageUserVO userVO = new ManageUserVO();
        userVO.setUserId(user.getUserId());
        userVO.setUsername(user.getUsername());
        userVO.setRealname(user.getRealname());
        userVO.setImg(user.getImg());
        if (StrUtil.isNotBlank(user.getImg())) {
            try {
                userVO.setImgUrl(fileStorageService.getUrl(user.getImg()));
            } catch (Exception ignored) {
            }
        }
        userVO.setMobile(user.getMobile());
        userVO.setEmail(user.getEmail());
        userVO.setPost(user.getPost());
        userVO.setDeptId(user.getDeptId());
        userVO.setSex(user.getSex());
        userVO.setStatus(user.getStatus());
        userVO.setPreferences(UserPreferenceSupport.parsePreferences(user.getUiPreferences()));
        return userVO;
    }

    private ExternalAuthBindingVO toBindingVO(ExternalAuthIdentity identity) {
        ExternalAuthBindingVO vo = new ExternalAuthBindingVO();
        vo.setProvider(identity.getProvider());
        vo.setName(providerName(identity.getProvider()));
        vo.setSubject(identity.getSubject());
        vo.setEmail(identity.getEmail());
        vo.setDisplayName(identity.getDisplayName());
        vo.setAvatarUrl(identity.getAvatarUrl());
        vo.setBindTime(identity.getBindTime());
        vo.setLastLoginTime(identity.getLastLoginTime());
        return vo;
    }

    private ExternalProfile fetchExternalProfile(String provider, ExternalAuthProperties.ProviderConfig config, String code, String redirectUri) {
        return switch (provider) {
            case "google" -> fetchGoogleProfile(config, code, redirectUri);
            case "wechat" -> fetchWechatProfile(config, code);
            default -> throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Unsupported external auth provider");
        };
    }

    private ExternalProfile fetchGoogleProfile(ExternalAuthProperties.ProviderConfig config, String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        ResponseEntity<String> tokenResponse = restTemplate.exchange(
                "https://oauth2.googleapis.com/token",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        JSONObject tokenJson = parseJsonResponse(tokenResponse);
        String accessToken = tokenJson.getString("access_token");
        if (StrUtil.isBlank(accessToken)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "Google token response is missing access_token");
        }

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        ResponseEntity<String> userResponse = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                String.class
        );
        JSONObject userJson = parseJsonResponse(userResponse);
        String subject = userJson.getString("sub");
        if (StrUtil.isBlank(subject)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "Google profile is missing sub");
        }
        ExternalProfile profile = new ExternalProfile();
        profile.setSubject(subject);
        profile.setEmail(userJson.getString("email"));
        profile.setEmailVerified(userJson.getBooleanValue("email_verified"));
        profile.setDisplayName(userJson.getString("name"));
        profile.setAvatarUrl(userJson.getString("picture"));
        profile.setRawProfile(userJson.toJSONString());
        return profile;
    }

    private ExternalProfile fetchWechatProfile(ExternalAuthProperties.ProviderConfig config, String code) {
        String tokenUrl = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", config.getClientId())
                .queryParam("secret", config.getClientSecret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUriString();
        JSONObject tokenJson = parseJsonResponse(restTemplate.exchange(tokenUrl, HttpMethod.GET, HttpEntity.EMPTY, String.class));
        String accessToken = tokenJson.getString("access_token");
        String openId = tokenJson.getString("openid");
        if (StrUtil.isBlank(accessToken) || StrUtil.isBlank(openId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "WeChat token response is invalid");
        }

        String profileUrl = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/userinfo")
                .queryParam("access_token", accessToken)
                .queryParam("openid", openId)
                .queryParam("lang", "zh_CN")
                .build()
                .encode()
                .toUriString();
        JSONObject userJson = parseJsonResponse(restTemplate.exchange(profileUrl, HttpMethod.GET, HttpEntity.EMPTY, String.class));
        String subject = StrUtil.blankToDefault(userJson.getString("unionid"), userJson.getString("openid"));
        if (StrUtil.isBlank(subject)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "WeChat profile is missing subject");
        }
        ExternalProfile profile = new ExternalProfile();
        profile.setSubject(subject);
        profile.setEmail(null);
        profile.setEmailVerified(false);
        profile.setDisplayName(userJson.getString("nickname"));
        profile.setAvatarUrl(userJson.getString("headimgurl"));
        profile.setRawProfile(userJson.toJSONString());
        return profile;
    }

    private JSONObject parseJsonResponse(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful() || StrUtil.isBlank(response.getBody())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "External auth provider request failed");
        }
        JSONObject json = JSON.parseObject(response.getBody());
        if (json.containsKey("error") || json.containsKey("errcode")) {
            Integer errcode = json.getInteger("errcode");
            if (errcode == null || errcode != 0) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "External auth provider returned an error");
            }
        }
        return json;
    }

    private String buildProviderAuthorizeUrl(String provider, ExternalAuthProperties.ProviderConfig config, String redirectUri, String state) {
        if ("google".equals(provider)) {
            return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                    .queryParam("client_id", config.getClientId())
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("response_type", "code")
                    .queryParam("scope", "openid email profile")
                    .queryParam("state", state)
                    .queryParam("access_type", "offline")
                    .queryParam("prompt", "select_account")
                    .build()
                    .encode()
                    .toUriString();
        }
        String url = UriComponentsBuilder.fromHttpUrl("https://open.weixin.qq.com/connect/qrconnect")
                .queryParam("appid", config.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_login")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        return url + "#wechat_redirect";
    }

    private ExternalAuthProperties.ProviderConfig requireUsableProvider(String provider) {
        ExternalAuthProperties.ProviderConfig config = externalAuthProperties.getProvider(provider);
        if (config == null || !config.isUsable()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "External auth provider is not configured");
        }
        return config;
    }

    private String normalizeProvider(String provider) {
        String normalized = StrUtil.emptyToDefault(provider, "").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Unsupported external auth provider");
        }
        return normalized;
    }

    private String providerName(String provider) {
        return switch (provider) {
            case "google" -> "Google";
            case "wechat" -> "WeChat";
            default -> provider;
        };
    }

    private String resolveProviderCallbackUri(String provider, ExternalAuthProperties.ProviderConfig config, HttpServletRequest request) {
        if (StrUtil.isNotBlank(config.getRedirectUri())) {
            return config.getRedirectUri();
        }
        return requestBaseUrl(request) + "/auth/external/" + provider + "/callback";
    }

    private String sanitizeFrontendRedirect(String redirect, HttpServletRequest request) {
        String fallback = defaultFrontendRedirect(request);
        if (StrUtil.isBlank(redirect)) {
            return fallback;
        }
        String frontendBase = frontendBaseUrl(request);
        if (redirect.startsWith("/") && !redirect.startsWith("//")) {
            return frontendBase + redirect;
        }
        try {
            URI candidate = URI.create(redirect);
            if (candidate.getScheme() == null || candidate.getHost() == null) {
                return frontendBase + "/" + redirect.replaceFirst("^/+", "");
            }
            if (isAllowedRedirectOrigin(candidate, request)) {
                return redirect;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return fallback;
    }

    private String defaultFrontendRedirect(HttpServletRequest request) {
        if (StrUtil.isNotBlank(externalAuthProperties.getFrontendRedirectUri())) {
            return externalAuthProperties.getFrontendRedirectUri();
        }
        return frontendBaseUrl(request) + "/#/login";
    }

    private String frontendBaseUrl(HttpServletRequest request) {
        String configured = externalAuthProperties.getFrontendRedirectUri();
        if (StrUtil.isNotBlank(configured)) {
            try {
                URI uri = URI.create(configured);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    return origin(uri);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        String originHeader = request.getHeader(HttpHeaders.ORIGIN);
        if (StrUtil.isNotBlank(originHeader)) {
            return originHeader;
        }
        return requestBaseUrl(request);
    }

    private boolean isAllowedRedirectOrigin(URI candidate, HttpServletRequest request) {
        if (!("http".equalsIgnoreCase(candidate.getScheme()) || "https".equalsIgnoreCase(candidate.getScheme()))) {
            return false;
        }
        for (String allowed : allowedOrigins(request)) {
            try {
                if (sameOrigin(candidate, URI.create(allowed))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private List<String> allowedOrigins(HttpServletRequest request) {
        List<String> origins = new ArrayList<>();
        if (StrUtil.isNotBlank(externalAuthProperties.getFrontendRedirectUri())) {
            try {
                origins.add(origin(URI.create(externalAuthProperties.getFrontendRedirectUri())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        String originHeader = request.getHeader(HttpHeaders.ORIGIN);
        if (StrUtil.isNotBlank(originHeader)) {
            origins.add(originHeader);
        }
        origins.add(requestBaseUrl(request));
        return origins;
    }

    private String requestBaseUrl(HttpServletRequest request) {
        String proto = firstHeader(request, "X-Forwarded-Proto", request.getScheme());
        String host = firstHeader(request, "X-Forwarded-Host", request.getHeader(HttpHeaders.HOST));
        if (StrUtil.isBlank(host)) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        String prefix = firstHeader(request, "X-Forwarded-Prefix", request.getContextPath());
        return proto + "://" + host + StrUtil.emptyToDefault(prefix, "");
    }

    private String firstHeader(HttpServletRequest request, String header, String fallback) {
        String value = request.getHeader(header);
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
    }

    private boolean sameOrigin(URI a, URI b) {
        return Objects.equals(a.getScheme(), b.getScheme())
                && Objects.equals(StrUtil.emptyToDefault(a.getHost(), "").toLowerCase(Locale.ROOT),
                StrUtil.emptyToDefault(b.getHost(), "").toLowerCase(Locale.ROOT))
                && effectivePort(a) == effectivePort(b);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String origin(URI uri) {
        int port = uri.getPort();
        String portPart = port >= 0 ? ":" + port : "";
        return uri.getScheme() + "://" + uri.getHost() + portPart;
    }

    private String appendFrontendQuery(String redirect, Map<String, String> params) {
        int hashIndex = redirect.indexOf('#');
        if (hashIndex >= 0) {
            String beforeHash = redirect.substring(0, hashIndex + 1);
            String fragment = redirect.substring(hashIndex + 1);
            return beforeHash + appendQuery(fragment, params);
        }
        return appendQuery(redirect, params);
    }

    private String appendQuery(String value, Map<String, String> params) {
        StringBuilder builder = new StringBuilder(value);
        builder.append(value.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            builder.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Data
    private static class AuthState {
        private String state;
        private String provider;
        private String scene;
        private Long userId;
        private String redirect;
    }

    @Data
    private static class ExternalProfile {
        private String subject;
        private String email;
        private Boolean emailVerified;
        private String displayName;
        private String avatarUrl;
        private String rawProfile;
    }
}
