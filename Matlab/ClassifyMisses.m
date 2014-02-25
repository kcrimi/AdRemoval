clear('stats','falseAlarm','accuracy','miss');
for iter = 1:100
    perm = randperm(length(Attributes));
    AttPerm = Attributes(perm,:);
    IndPerm = Index(perm,:);
    blockSize = ceil(length(Attributes)/10);
    A={};
    for i = 1:9
        A{i} = AttPerm(blockSize*(i-1)+1:blockSize*i,:);
        B{i} = [IndPerm(blockSize*(i-1)+1:blockSize*i,:),zeros(blockSize,1)];
    end
    A{10} = AttPerm(blockSize*9:end,:);
    B{10} = [IndPerm(blockSize*9:end,:),zeros(length(IndPerm)-(blockSize*9)+1,1)];
    
    %%Cross validation 
    for select = 1:10
        train = [];
        test = A{select};
        for i = 1:10
            if select ~= i
                train = [train;A{i}];
            end
        end
        %class = classify(test(:,2:6),train(:,2:6),train(:,7),'linear');
        training = train(:,[1:end-3,end-1]);
        trainingCats = train(:,end);
        coefs = LDA(training, trainingCats);
        
        testing = [ones(size(test,1),1),test(:,[1:end-3,end-1])];
        scores = testing * coefs(1:2,:)';
        [~,class] = max(scores');
        class = class'-1;
        AllCoefs(:,:,iter,select) = coefs(1:2,:);
        
        accuracy(select,1) = sum(class == test(:,end))/length(test);
        falseAlarm(select,1) = sum(class(test(:,end)==0)==1)/length(test);
        miss(select,1)= sum(class(test(:,end)==1)==0)/length(test);
        
        B{select}(:,4) = B{select}(:,4)+(class~= test(:,end));
    end
    
     for block = 1:10
         for scene = 1:size(A{block},1)
             [~,ind] = ismember(B{block}(scene,1:3),Index(:,1:3),'rows');
             Index(ind,4) = Index(ind,4)+B{block}(scene,4);
         end
     end
    
    stats(iter,1) = mean(accuracy);
    stats(iter,2) = mean(falseAlarm);
    stats(iter,3) = mean(miss);
end
mean(stats)

