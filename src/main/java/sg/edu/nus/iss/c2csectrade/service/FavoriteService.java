package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.entity.Favorite;
import sg.edu.nus.iss.c2csectrade.mapper.FavoriteMapper;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    /** Idempotent: favouriting twice leaves one row, so the button never errors. */
    public void add(Long userId, Long productId) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favoriteMapper.insert(favorite);
    }

    public void remove(Long userId, Long productId) {
        favoriteMapper.delete(userId, productId);
    }

    public boolean isFavorited(Long userId, Long productId) {
        return favoriteMapper.selectByUserAndProduct(userId, productId) != null;
    }

    public List<Favorite> list(Long userId) {
        return favoriteMapper.selectByUserId(userId);
    }

    public int count(Long userId) {
        return favoriteMapper.countByUserId(userId);
    }
}
