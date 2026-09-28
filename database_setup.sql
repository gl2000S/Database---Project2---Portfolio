-- Create the database.
create database if not exists csx370_mb_platform;

-- Use the created database.
use csx370_mb_platform;

-- Create the user table.
create table if not exists user (
    userId int auto_increment,
    username varchar(255) not null,
    password varchar(255) not null,
    firstName varchar(255) not null,
    lastName varchar(255) not null,
    primary key (userId),
    unique (username),
    constraint userName_min_length check (char_length(trim(userName)) >= 2),
    constraint firstName_min_length check (char_length(trim(firstName)) >= 2),
    constraint lastName_min_length check (char_length(trim(lastName)) >= 2)
);

-- Posts made by users
create table if not exists post (
    postId int auto_increment,
    userId int not null,
    content varchar(1400) not null,
    postDate datetime not null default current_timestamp,
    primary key (postId),
    foreign key (userId) references user(userId)
);

-- Hashtags (unique tag words)
create table if not exists hashtag (
    hashtagId int auto_increment,
    tag varchar(255) not null,
    primary key (hashtagId),
    unique (tag)
);

-- Junction table: which hashtags belong to which post
create table if not exists post_hashtag (
    postId int not null,
    hashtagId int not null,
    primary key (postId, hashtagId),
    foreign key (postId) references post(postId),
    foreign key (hashtagId) references hashtag(hashtagId)
);

-- Hearts/likes on posts
create table if not exists heart (
    userId int not null,
    postId int not null,
    primary key (userId, postId),
    foreign key (userId) references user(userId),
    foreign key (postId) references post(postId)
);

-- Bookmarks
create table if not exists bookmark (
    userId int not null,
    postId int not null,
    primary key (userId, postId),
    foreign key (userId) references user(userId),
    foreign key (postId) references post(postId)
);

-- Comments on posts
create table if not exists comment (
    commentId int auto_increment,
    postId int not null,
    userId int not null,
    content varchar(1400) not null,
    commentDate datetime not null default current_timestamp,
    primary key (commentId),
    foreign key (postId) references post(postId),
    foreign key (userId) references user(userId)
);

-- Follows between users
create table if not exists follow (
    followerId int not null,
    followedId int not null,
    primary key (followerId, followedId),
    foreign key (followerId) references user(userId),
    foreign key (followedId) references user(userId)
);

-- Upvotes on posts (for trending page)
create table if not exists upvote (
    userId int not null,
    postId int not null,
    primary key (userId, postId),
    foreign key (userId) references user(userId),
    foreign key (postId) references post(postId)
);