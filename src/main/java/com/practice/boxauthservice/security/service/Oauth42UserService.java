package com.practice.boxauthservice.security.service;

import com.practice.boxauthservice.security.service.dto.PostUsersResponseDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@AllArgsConstructor
public class Oauth42UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private final DiscoveryClient discoveryClient;

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    try {
      ClientRegistration clientRegistration = userRequest.getClientRegistration();
      OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService = new DefaultOAuth2UserService();
      OAuth2User oAuth2User = oAuth2UserService.loadUser(userRequest);
      PostUsersResponseDto resultDto = signUp(clientRegistration, oAuth2User);
      String uuid = resultDto.getUuid();
      String role = resultDto.getRole();
      String profileImagePath = resultDto.getProfileImagePath();
      String profileImageUrl = resultDto.getProfileImageUrl();
      if (uuid == null || uuid.isEmpty()
          || role == null || role.isEmpty()
          || profileImagePath == null || profileImagePath.isEmpty()
          || profileImageUrl == null || profileImageUrl.isEmpty()) {
        throw new OAuth2AuthenticationException("Registration failed");
      }
      Map<String, Object> mutableAttributes = new HashMap<>(oAuth2User.getAttributes());
      Map<String, Object> userInfo = new HashMap<>();
      userInfo.put("uuid", uuid);
      userInfo.put("role", role);
      userInfo.put("profileImagePath", profileImagePath);
      userInfo.put("profileImageUrl", profileImageUrl);
      mutableAttributes.put("user-info", userInfo);
      return new DefaultOAuth2User(oAuth2User.getAuthorities(), mutableAttributes,
          clientRegistration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
    } catch (Exception e) {
      throw new OAuth2AuthenticationException("Registration failed");
    }
  }

  private PostUsersResponseDto signUp(ClientRegistration clientRegistration,
      OAuth2User oAuth2User) {
    String registrationId = clientRegistration.getRegistrationId();
    if (registrationId.equals("42api")) {
      return signUp42User(oAuth2User, clientRegistration);
    }
    throw new OAuth2AuthenticationException("not supported registrationId");
  }

  private PostUsersResponseDto signUp42User(OAuth2User oAuth2User,
      ClientRegistration clientRegistration) {
    Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
//    ArrayList<Integer> cursus_ids = (ArrayList<Integer>) attributes.get("cursus_ids"); // 보류
    ArrayList<Map<String, Object>> campus_users = (ArrayList<Map<String, Object>>) attributes.get(
        "campus_users");
    Map<String, Object> firstMap = campus_users.get(0);

    String nickname = attributes.get("login").toString();
    int campus_id = Integer.parseInt(String.valueOf(firstMap.get("campus_id")));
    String role = "ROLE_AUTH_USER";
    return postUserWithRestTemplate(nickname, campus_id, role);
  }

  private PostUsersResponseDto postUserWithRestTemplate(String nickname, int campus_id,
      String role) {
    String userServiceUrl = getServiceUrl("user-service");

    RestTemplate restTemplate = new RestTemplate();
    String endpoint = userServiceUrl + "/users/42";
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("nickname", nickname);
    requestBody.put("campusId", campus_id);
    requestBody.put("role", role);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
    try {
      ResponseEntity<PostUsersResponseDto> response = restTemplate.postForEntity(endpoint,
          requestEntity,
          PostUsersResponseDto.class);
      return response.getBody();
      // 성공시 200, 201 에 따라 uuid, nickname 쌍을 DB에 저장해서 관리하자
    } catch (Exception e) {
      throw new OAuth2AuthenticationException("Registration failed");
    }
  }

  private String getServiceUrl(String serviceName) {
    List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
    if (instances != null && !instances.isEmpty()) {
      return instances.get(0).getUri().toString();
    }
    throw new OAuth2AuthenticationException("discoveryClient failed");
  }
}
