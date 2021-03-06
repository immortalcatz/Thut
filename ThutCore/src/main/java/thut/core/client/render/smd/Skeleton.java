package thut.core.client.render.smd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import thut.core.client.render.model.Vertex;
import thut.core.client.render.smd.SkeletonAnimation.SkeletonFrame;

public class Skeleton
{
    public static class Bone
    {
        public final int                            id;
        public final int                            parentId;
        final String                                name;
        public final HashSet<Bone>                  children           = new HashSet<>();
        public final HashMap<BoneVertex, Float>     vertices           = new HashMap<>();
        public Matrix4f                             rest               = new Matrix4f();
        public Matrix4f                             restInverse        = new Matrix4f();
        public Matrix4f                             deform             = new Matrix4f();
        public Matrix4f                             deformInverse      = new Matrix4f();
        public HashMap<String, ArrayList<Matrix4f>> animatedTransforms = new HashMap<>();
        public Bone                                 parent;
        final Skeleton                              skeleton;

        public Bone(int id, int parentId, String name, Skeleton skeleton)
        {
            this.skeleton = skeleton;
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        public Bone(String line, Skeleton skeleton)
        {
            this.skeleton = skeleton;
            String[] args = parse(line);
            id = Integer.parseInt(args[0]);
            name = args[1];
            parentId = Integer.parseInt(args[2]);
        }

        public void applyDeform()
        {
            for (BoneVertex v : vertices.keySet())
            {
                v.applyTransform(deform, vertices.get(v));
            }
            for (Bone b : children)
                b.applyDeform();
        }

        public void clear()
        {
            reset();
            restInverse.setIdentity();
            rest.setIdentity();
            animatedTransforms.clear();
            for (Bone b : children)
                b.clear();
        }

        // TODO get this properly applying parent deforms.
        public void deform()
        {
            SkeletonAnimation animation = skeleton.pose;
            if (animation != null)
            {
                ArrayList<Matrix4f> precalc = this.animatedTransforms.get(animation.animationName);
                Matrix4f animated = precalc.get(animation.currentIndex);
                Matrix4f dAnimated = Matrix4f.mul(animated, this.restInverse, null);
                Matrix4f.mul(deform, dAnimated, deform);
                Matrix4f.invert(deform, deformInverse);
            }
            for (Bone b : children)
                b.deform();
        }

        public void invertRestMatrix()
        {
            this.restInverse = Matrix4f.invert(this.rest, null);
        }

        String[] parse(String line)
        {
            String[] ret = new String[3];
            int indexQuoteStart = line.indexOf("\"");
            int indexQuoteEnd = line.lastIndexOf("\"");
            ret[0] = line.substring(0, indexQuoteStart - 1);
            ret[1] = line.substring(indexQuoteStart + 1, indexQuoteEnd);
            ret[2] = line.substring(indexQuoteEnd + 2);
            return ret;
        }

        public void preloadAnimation(SkeletonFrame key, Matrix4f animated)
        {
            ArrayList<Matrix4f> transforms;
            if (this.animatedTransforms.containsKey(key.animation.animationName))
            {
                transforms = this.animatedTransforms.get(key.animation.animationName);
            }
            else
            {
                transforms = new ArrayList<>();
            }
            ensureIndex(transforms, key.time);
            transforms.set(key.time, animated);
            this.animatedTransforms.put(key.animation.animationName, transforms);
        }

        private void reform(Matrix4f parentMatrix)
        {
            this.rest = Matrix4f.mul(parentMatrix, this.rest, null);
            reformChildren();
        }

        public void reformChildren()
        {
            for (Bone child : this.children)
            {
                child.reform(this.rest);
            }
        }

        public void reset()
        {
            deform.setIdentity();
            deformInverse.setIdentity();
        }

        public void setRest(Matrix4f resting)
        {
            this.rest = resting;
        }

        @Override
        public String toString()
        {
            return id + " " + name + " " + parentId;
        }
    }
    public static class BoneVertex extends Vertex
    {
        private final Vector4f originalPos;
        public Vector4f        positionDeform = new Vector4f();
        private final Vector4f originalNormal;
        public Vector4f        normalDeform   = new Vector4f();
        public final int       id;
        public float           xn;
        public float           yn;
        public float           zn;

        public BoneVertex(float x, float y, float z, float xn, float yn, float zn, int id)
        {
            super(x, y, z);
            this.xn = xn;
            this.yn = yn;
            this.zn = zn;
            this.id = id;
            this.originalPos = new Vector4f(x, y, z, 1.0F);
            this.originalNormal = new Vector4f(xn, yn, zn, 0.0F);
        }

        public void applyDeformation()
        {
            if (this.positionDeform == null)
            {
                this.x = this.originalPos.x;
                this.y = this.originalPos.y;
                this.z = this.originalPos.z;
            }
            else
            {
                this.x = this.positionDeform.x;
                this.y = this.positionDeform.y;
                this.z = this.positionDeform.z;
            }
            if (this.normalDeform == null)
            {
                this.xn = this.originalNormal.x;
                this.yn = this.originalNormal.y;
                this.zn = this.originalNormal.z;
            }
            else
            {
                this.xn = this.normalDeform.x;
                this.yn = this.normalDeform.y;
                this.zn = this.normalDeform.z;
            }
        }

        public void applyTransform(Matrix4f transform, float weight)
        {
            if (transform != null)
            {
                this.positionDeform = new Vector4f();
                this.normalDeform = new Vector4f();
                Vector4f loc = Matrix4f.transform(transform, this.originalPos, null);
                Vector4f normal = Matrix4f.transform(transform, this.originalNormal, null);
                loc.scale(weight);
                normal.scale(weight);
                Vector4f.add(loc, this.positionDeform, this.positionDeform);
                Vector4f.add(normal, this.normalDeform, this.normalDeform);

                Matrix4f.transform(transform, this.originalPos, this.positionDeform);
                Matrix4f.transform(transform, this.originalNormal, this.normalDeform);
            }
        }

        public void reset()
        {
            this.positionDeform = null;
            this.normalDeform = null;
        }

        @Override
        public String toString()
        {
            return id + ":" + x + "," + y + "," + z;
        }
    }
    public static void ensureIndex(ArrayList<?> a, int i)
    {
        while (a.size() <= i)
        {
            a.add(null);
        }
    }
    public HashMap<Integer, Bone> boneMap = new HashMap<>();

    public final SMDModel         model;

    public SkeletonAnimation      pose;

    public Bone                   root;

    public Skeleton(SMDModel model)
    {
        this.model = model;
    }

    public void addBone(Bone bone)
    {
        if (boneMap.containsKey(bone.id)) throw new IllegalArgumentException("Already has bone of id " + bone.id);
        boneMap.put(bone.id, bone);
    }

    public void applyChange()
    {
        for (Bone b : boneMap.values())
        {
            for (BoneVertex v : b.vertices.keySet())
            {
                v.applyDeformation();
            }
        }
    }

    public void applyPose()
    {
        if (pose.lastPoseChange == pose.currentIndex) return;
        pose.lastPoseChange = pose.currentIndex;
        reset();
        root.deform();
        root.applyDeform();
        applyChange();
    }

    public Bone getBone(int id)
    {
        return boneMap.get(id);
    }

    public void init()
    {
        for (Bone bone : boneMap.values())
        {
            if (bone.parentId != -1)
            {
                Bone parent = boneMap.get(bone.parentId);
                bone.parent = parent;
                parent.children.add(bone);
            }
        }
        for (Bone b : boneMap.values())
        {
            if (b.parent == null && !b.children.isEmpty())
            {
                root = b;
            }
        }
    }

    private void initPose()
    {
        System.out.println(pose.animationName + " " + pose.frames.size());

        SkeletonFrame frame = pose.frames.get(0);
        pose.reset();
        pose.precalculateAnimation();
        for (Integer i : frame.positions.keySet())
        {
            Matrix4f trans = frame.positions.get(i);
            Bone bone = boneMap.get(i);
            bone.setRest(trans);
        }
        root.reformChildren();
        for (Bone b : boneMap.values())
        {
            b.invertRestMatrix();
        }
        pose.reform();
    }

    public void reset()
    {
        for (Bone b : boneMap.values())
        {
            b.reset();
            for (BoneVertex v : b.vertices.keySet())
            {
                v.reset();
            }
        }
    }

    public void setPose(SkeletonAnimation pose)
    {
        if (this.pose == pose) return;
        this.pose = pose;
        initPose();
    }
}
