package com.example.santa.domain.user.service;

import com.example.santa.domain.category.entity.Category;
import com.example.santa.domain.category.repository.CategoryRepository;
import com.example.santa.domain.preferredcategory.dto.CategoriesRequestDto;
import com.example.santa.domain.preferredcategory.dto.PreferredCategoryRequestDto;
import com.example.santa.domain.preferredcategory.dto.PreferredCategoryResponseDto;
import com.example.santa.domain.preferredcategory.entity.PreferredCategory;
import com.example.santa.domain.preferredcategory.repository.PreferredCategoryRepository;
import com.example.santa.domain.user.dto.UserResponseDto;
import com.example.santa.domain.user.dto.UserSignInRequestDto;
import com.example.santa.domain.user.dto.UserSignupRequestDto;
import com.example.santa.domain.user.dto.UserUpdateRequestDto;
import com.example.santa.domain.user.entity.Password;
import com.example.santa.domain.user.entity.Role;
import com.example.santa.domain.user.entity.User;
import com.example.santa.domain.user.repository.UserRepository;
import com.example.santa.domain.usermountain.dto.UserMountainResponseDto;
import com.example.santa.global.exception.ExceptionCode;
import com.example.santa.global.exception.ServiceLogicException;
import com.example.santa.global.security.jwt.JwtToken;
import com.example.santa.global.security.jwt.JwtTokenProvider;
import com.example.santa.global.util.mapsturct.PreferredCategoryResponseDtoMapper;
import com.example.santa.global.util.mapsturct.UserMountainResponseDtoMapper;
import com.example.santa.global.util.mapsturct.UserResponseDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Transactional
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserResponseDtoMapper userResponseDtoMapper;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PreferredCategoryRepository preferredCategoryRepository;
    private final CategoryRepository categoryRepository;

    private final UserMountainResponseDtoMapper userMountainResponseDtoMapper;
    private final PreferredCategoryResponseDtoMapper preferredCategoryResponseDtoMapper;

    @Transactional
    @Override
    public Long signup(UserSignupRequestDto request) {
        if (checkEmailDuplicate(request.getEmail())) {
            throw new ServiceLogicException(ExceptionCode.EMAIL_ALREADY_EXISTS);
        }
        if (checkNicknameDuplicate(request.getNickname())) {
            throw new ServiceLogicException(ExceptionCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(new Password(request.getPassword()))
                .name(request.getName())
                .nickname(request.getNickname())
                .phoneNumber(request.getPhoneNumber())
                .role(Role.USER)
                .build();

        User save = userRepository.save(user);
        return save.getId();
    }

    @Override
    public Boolean checkEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Boolean checkNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    /*
     * email + password 를 받아서 Authentication 객체 생성
     * authenticate 메서드 를 사용해서 User 검증 -> 이때 만들어 놓은 loadUserByUsername 메서드가 실행 됨
     * 검증 성공하면 Authentication 객체를 기반으로 JWT 토큰 생성
     * */
    @Transactional
    @Override
    public JwtToken signIn(UserSignInRequestDto userSignInRequestDto) {
        User user = userRepository.findByEmail(userSignInRequestDto.getEmail())
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        UsernamePasswordAuthenticationToken authenticationToken
                = new UsernamePasswordAuthenticationToken(userSignInRequestDto.getEmail(), userSignInRequestDto.getPassword(), user.getAuthorities());
        authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        JwtToken jwtToken = jwtTokenProvider.generateToken(authenticationToken);
        return jwtToken;
    }

    @Transactional
    @Override
    public String generateAccessToken(String refreshToken) {
        return jwtTokenProvider.generateAccessTokenFromRefreshToken(refreshToken);
    }

    @Override
    public UserResponseDto findUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        log.info("role {}", user.getRole());
        UserResponseDto dto = userResponseDtoMapper.toDto(user);
        log.info("dto = {}", dto);
        return dto;
    }

    @Override
    public Page<UserResponseDto> findAllUser(Pageable pageable) {
        return userRepository.findAll(pageable).map(userResponseDtoMapper::toDto);
    }


    @Transactional
    @Override
    public UserResponseDto updateUser(String email, UserUpdateRequestDto userUpdateRequestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND))
                .update(userUpdateRequestDto.getNickname()
                        , userUpdateRequestDto.getPhoneNumber()
                        , userUpdateRequestDto.getImage());

        return userResponseDtoMapper.toDto(user);
    }


    @Transactional
    @Override
    public String changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        user.getPasswordForChange().changePassword(oldPassword, newPassword);
        return user.getEmail();
    }

    @Override
    public Page<UserMountainResponseDto> findAllUserMountains(String email, Pageable pageable) {
        User byEmail = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        Page<UserMountainResponseDto> pageDto = userRepository.findUserMountainsByUserId(byEmail.getId(), pageable)
                .map(userMountainResponseDtoMapper::toDto);
        return pageDto;
    }

    @Transactional
    @Override
    public String resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        user.getPasswordForChange().resetPassword(newPassword);
        return user.getEmail();
    }

    @Transactional
    @Override
    public Long savePreferredCategory(String email, PreferredCategoryRequestDto preferredCategoryRequestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        Category category = categoryRepository.findById(preferredCategoryRequestDto.getCategoryId())
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.CATEGORY_NOT_FOUND));
        PreferredCategory save = preferredCategoryRepository.save(PreferredCategory.builder()
                .user(user)
                .category(category)
                .build());
        return save.getId();
    }

    @Transactional
    @Override
    public List<Long> savePreferredCategories(String email, List<Long> categoryIds) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));

//        List<Long> createdPreferredCategories = new ArrayList<>();

//        for (Long categoryId : categoryIds) {
//            Category category = categoryRepository.findById(categoryId)
//                    .orElseThrow(() -> new ServiceLogicException(ExceptionCode.CATEGORY_NOT_FOUND));
//            PreferredCategory preferredCategory = PreferredCategory.builder()
//                    .user(user)
//                    .category(category)
//                    .build();
//            PreferredCategory saved = preferredCategoryRepository.save(preferredCategory);
//            createdPreferredCategories.add(saved.getId());
//        }

//        categoryIds.forEach((id) -> {
//            PreferredCategory save = preferredCategoryRepository.save(PreferredCategory.builder()
//                    .user(user)
//                    .category(categoryRepository.findById(id).orElseThrow(() -> new ServiceLogicException(ExceptionCode.CATEGORY_NOT_FOUND)))
//                    .build());
//            createdPreferredCategories.add(save.getId());
//        });
        List<Long> createdPreferredCategories =
                categoryIds.stream().map((id) ->
                        preferredCategoryRepository.save(
                                PreferredCategory.builder()
                                        .user(user)
                                        .category(categoryRepository.findById(id).orElseThrow(() -> new ServiceLogicException(ExceptionCode.CATEGORY_NOT_FOUND)))
                                        .build()).getId()).toList();

        return createdPreferredCategories;
    }

    @Override
    public List<PreferredCategoryResponseDto> findAllPreferredCategories(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        List<PreferredCategory> allByUserId = preferredCategoryRepository.findAllByUserId(user.getId());
        List<PreferredCategoryResponseDto> dtoList = preferredCategoryResponseDtoMapper.toDtoList(allByUserId);
        return dtoList;
    }

    @Transactional
    @Override
    public void deleteAllPreferredCategory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.USER_NOT_FOUND));
        List<PreferredCategory> allByUserId = preferredCategoryRepository.findAllByUserId(user.getId());
        preferredCategoryRepository.deleteAll(allByUserId);
    }
}
