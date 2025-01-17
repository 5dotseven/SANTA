package com.example.santa.domain.challege.service;

import com.example.santa.domain.category.entity.Category;
import com.example.santa.domain.category.repository.CategoryRepository;
import com.example.santa.domain.challege.dto.ChallengeCreateDto;
import com.example.santa.domain.challege.dto.ChallengeResponseDto;
import com.example.santa.domain.challege.entity.Challenge;
import com.example.santa.domain.user.repository.UserRepository;
import com.example.santa.domain.userchallenge.repository.UserChallengeRepository;
import com.example.santa.global.exception.ExceptionCode;
import com.example.santa.global.exception.ServiceLogicException;
import com.example.santa.global.util.S3ImageService;
import com.example.santa.global.util.mapsturct.ChallengeResponseMapper;
import com.example.santa.domain.challege.repository.ChallengeRepository;
import com.example.santa.global.util.mapsturct.UserChallengeResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Service
@Slf4j
public class ChallengeServiceImpl implements ChallengeService{

    private final ChallengeRepository challengeRepository;
    private final ChallengeResponseMapper challengeResponseMapper;

    private final CategoryRepository categoryRepository;
    private final S3ImageService s3ImageService;

    @Autowired
    public ChallengeServiceImpl(ChallengeRepository challengeRepository, CategoryRepository categoryRepository, ChallengeResponseMapper challengeResponseMapper, S3ImageService s3ImageService) {
        this.challengeRepository = challengeRepository;
        this.categoryRepository = categoryRepository;
        this.challengeResponseMapper = challengeResponseMapper;
        this.s3ImageService = s3ImageService;
    }


    //CREATE
    @Transactional
    @Override
    public ChallengeResponseDto saveChallenge(ChallengeCreateDto challengeCreateDto) {
        Category category = categoryRepository.findById(challengeCreateDto.getCategoryId())
                .orElseThrow(() -> new ServiceLogicException(ExceptionCode.CATEGORY_NOT_FOUND));

        MultipartFile imageFile = challengeCreateDto.getImageFile();
        String imageUrl = "https://s3.ap-northeast-2.amazonaws.com/elice.santa/challenge_default_image.png";
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = s3ImageService.upload(imageFile);
        }


        Challenge save = challengeRepository.save(Challenge.builder()
                .category(category)
                .name(challengeCreateDto.getName())
                .description(challengeCreateDto.getDescription())
                .clearStandard(challengeCreateDto.getClearStandard())
                .image(imageUrl)
                .build());
        return challengeResponseMapper.toDto(save);
    }


    @Override
    public Page<ChallengeResponseDto> findAllChallenges(Pageable pageable){
        Page<Challenge> challenges =challengeRepository.findAll(pageable);
        return challenges.map(challengeResponseMapper::toDto);
    }


    @Override
    public ChallengeResponseDto findChallengeById(Long id) {
        return challengeRepository.findById(id)
                .map(challengeResponseMapper::toDto)
                .orElse(null);
    }


    @Override
    public ChallengeResponseDto updateChallenge(Long id, ChallengeCreateDto challengeCreateDto) {
        MultipartFile imageFile = challengeCreateDto.getImageFile();
        String imageUrl = challengeCreateDto.getImage();
        if (imageFile != null && !imageFile.isEmpty()) {
            if(!Objects.equals(imageUrl, "https://s3.ap-northeast-2.amazonaws.com/elice.santa/challenge_default_image.png")){
                s3ImageService.deleteImageFromS3(imageUrl);
            }

            imageUrl = s3ImageService.upload(imageFile);
        }
        ChallengeResponseDto result = null;
        if (challengeRepository.existsById(id)) {
            Challenge challenge = challengeRepository.findById(id).get();
            challenge.setName(challengeCreateDto.getName());
            challenge.setDescription(challengeCreateDto.getDescription());
            challenge.setImage(challengeCreateDto.getImage());
            challenge.setClearStandard(challengeCreateDto.getClearStandard());
            challenge = challengeRepository.save(challenge);
            result = challengeResponseMapper.toDto(challenge);
        }
        return result;
    }

    @Override
    public void deleteChallenge(Long id) {

        challengeRepository.deleteById(id);
    }

}

