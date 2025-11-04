package com.example.photoviewer;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private final List<Bitmap> imageList;

    public ImageAdapter(List<Bitmap> imageList) {
        this.imageList = imageList;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Bitmap bitmap = imageList.get(position);
        holder.imageView.setImageBitmap(bitmap);
        
        // 페이드 인 애니메이션
        holder.cardView.setAlpha(0f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(holder.cardView, "alpha", 0f, 1f);
        fadeIn.setDuration(400);
        fadeIn.setStartDelay(position * 100); // 순차적으로 나타나도록
        fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeIn.start();
        
        // 카드 호버 효과
        holder.cardView.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        MaterialCardView cardView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewItem);
            cardView = (MaterialCardView) itemView;
        }
    }
}


