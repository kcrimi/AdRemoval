clear Attributes
v = {Vid1, Vid2, Vid3, Vid4};
iter = 1;
last = 0;
Index = [];
for f = 1:4
    totalNeighborhood = 0;
    for scene = 1:(length(v{f})-1)
        Index = [Index;[f,scene-1,v{f}(scene,end)]];
        for a = 5:size(v{f},2)-1
            %Attributes(iter,a-4) = abs(v{f}(scene,a)-v{f}(end,a))/v{f}(end,a); %abs deviation
            Attributes(iter,a-4) = (v{f}(scene,a)-v{f}(end,a))/v{f}(end,a); %deviation
            %Attributes(iter,a-4) = v{f}(scene,a); %feed straight in
        end
        
%         neighborhood = v{f}(scene,3)-v{f}(scene,2);
%         divisor = 1;
%         if scene ~= 1
%             neighborhood = neighborhood + (v{f}(scene-1,3)-v{f}(scene-1,2));
%             divisor = divisor+1;
%         end
%         if scene ~= length(v{f})
%             neighborhood = neighborhood + (v{f}(scene+1,3)-v{f}(scene+1,2));
%             divisor = divisor+1;
%         end
%         Attributes(iter, size(v{f},2)-4) = neighborhood/divisor;% Neighborhood

        Attributes(iter,size(v{f},2)-4) = v{f}(scene,end);
%         Attributes(iter,size(v{f},2)-3) = f;
%         Attributes(iter,size(v{f},2)-2) = v{f}(scene,1);
        iter = iter+1;
    end
%     Attributes(last+1:end,size(v{f},2)-4);
%     Attributes(last+1:end,size(v{f},2)-4) = Attributes(last+1:end, size(v{f},2)-4)- mean(Attributes(last+1:end, size(v{f},2)-4));
%     
      % deMean data
%           for i= last+1:length(Attributes)
%               Attributes(i,1:end-3) = Attributes(i,1:end-3)-mean(Attributes(last+1:end,1:end-3));
%           end
    last=size(Attributes,2);
end
Index(1,end+1)=0;
clear ('a','f','i','iter','last','scene','v');